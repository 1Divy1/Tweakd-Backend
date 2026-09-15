package com.tweakdapp.backend.notification.internal;

import com.tweakdapp.backend.notification.NotificationService;
import com.tweakdapp.backend.notification.dto.NotificationDto;
import com.tweakdapp.backend.notification.dto.NotificationPageDto;
import com.tweakdapp.backend.notification.exception.NotificationNotFoundException;
import com.tweakdapp.backend.notification.internal.entities.NotificationEntity;
import com.tweakdapp.backend.notification.internal.push.NotificationsCreatedEvent;
import com.tweakdapp.backend.notification.internal.repositories.NotificationRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProfileService profileService;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   ProfileService profileService) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
        this.profileService = profileService;
    }

    @Override
    @Transactional
    public void push(UUID userId, String type, String title, String body, Map<String, Object> payload) {
        if (isFromBlockedActor(userId, payload)) {
            return;
        }
        NotificationEntity saved = notificationRepository.save(buildNotification(userId, type, title, body, payload));
        publishForPush(List.of(saved.getId()));
    }

    @Override
    @Transactional
    public void pushToAll(Collection<UUID> userIds, String type, String title, String body, Map<String, Object> payload) {
        List<NotificationEntity> notifications = new LinkedHashSet<>(userIds).stream()
                .filter(userId -> !isFromBlockedActor(userId, payload))
                .map(userId -> buildNotification(userId, type, title, body, payload))
                .toList();
        List<NotificationEntity> saved = notificationRepository.saveAll(notifications);
        publishForPush(saved.stream().map(NotificationEntity::getId).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageDto listNotifications(UUID userId, String cursor, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        List<NotificationEntity> rows;
        if (cursor == null) {
            rows = notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, limit);
        } else {
            Cursor decoded = Cursor.decode(cursor);
            rows = notificationRepository.findPageAfter(userId, decoded.createdAt(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<NotificationEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        NotificationEntity last = page.isEmpty() ? null : page.getLast();
        String nextCursor = hasMore ? Cursor.encode(last.getCreatedAt(), last.getId()) : null;

        Map<UUID, String> avatars = actorAvatars(page);
        return new NotificationPageDto(page.stream().map(n -> toDto(n, avatars)).toList(), nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationDto markRead(UUID userId, UUID notificationId) {
        NotificationEntity notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.setRead(true);
        return toDto(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    /**
     * Hands the new rows to {@code PushDispatcher}, which sends them to FCM asynchronously once this
     * transaction commits. Publishing rather than calling directly keeps delivery off the caller's
     * thread and guarantees a rolled-back notification is never pushed. An empty batch is a no-op.
     */
    private void publishForPush(List<UUID> notificationIds) {
        if (!notificationIds.isEmpty()) {
            eventPublisher.publishEvent(new NotificationsCreatedEvent(notificationIds));
        }
    }

    /**
     * Whether the notification's actor ({@code actor_id} in the payload) is separated from the
     * recipient by a block. Those are dropped, whichever module produced them; rows without an actor
     * (moderation, feedback, support) are never affected.
     */
    private boolean isFromBlockedActor(UUID recipientId, Map<String, Object> payload) {
        Object actor = payload == null ? null : payload.get("actor_id");
        if (actor == null) {
            return false;
        }
        try {
            return profileService.isHiddenFrom(recipientId, UUID.fromString(actor.toString()));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private NotificationEntity buildNotification(UUID userId, String type, String title, String body,
                                                 Map<String, Object> payload) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setPayload(payload == null ? Map.of() : payload);
        notification.setRead(false);
        return notification;
    }

    private NotificationDto toDto(NotificationEntity entity) {
        return toDto(entity, Map.of());
    }

    /**
     * Builds the DTO, adding {@code actor_avatar_url} to the payload when the row's actor has a
     * photo in {@code avatars}. The key is a read-time addition, never stored: avatar objects are
     * deleted from R2 when replaced, so a URL snapshotted at write time would go dead as soon as its
     * owner changed their photo. The stored payload map is copied, not mutated.
     */
    private NotificationDto toDto(NotificationEntity entity, Map<UUID, String> avatars) {
        Map<String, Object> payload = entity.getPayload();
        UUID actorId = actorIdOf(entity);
        String avatarUrl = actorId == null ? null : avatars.get(actorId);
        if (avatarUrl != null) {
            payload = new LinkedHashMap<>(payload);
            payload.put("actor_avatar_url", avatarUrl);
        }
        return new NotificationDto(
                entity.getId(),
                entity.getType(),
                entity.getTitle(),
                entity.getBody(),
                payload,
                entity.isRead(),
                entity.getCreatedAt());
    }

    /**
     * Current avatar URL for every distinct actor on the page, in one batch lookup. Covers every
     * producer that records an {@code actor_id} — Spring listeners and Postgres-written rows such as
     * {@code dm} alike. Actors without a photo are simply absent, so the client falls back to their
     * initial.
     */
    private Map<UUID, String> actorAvatars(List<NotificationEntity> page) {
        Set<UUID> actorIds = new LinkedHashSet<>();
        for (NotificationEntity n : page) {
            UUID actorId = actorIdOf(n);
            if (actorId != null) {
                actorIds.add(actorId);
            }
        }
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> avatars = new HashMap<>();
        for (ProfileSearchResultDto profile : profileService.findByIds(actorIds)) {
            if (profile.avatarUrl() != null && !profile.avatarUrl().isBlank()) {
                avatars.put(profile.id(), profile.avatarUrl());
            }
        }
        return avatars;
    }

    /** The payload's {@code actor_id}, or null when absent (system notifications) or malformed. */
    private static UUID actorIdOf(NotificationEntity entity) {
        Object raw = entity.getPayload() == null ? null : entity.getPayload().get("actor_id");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Keyset cursor over (created_at, id) desc. Encoded as base64url of "ISO-instant|uuid" so the
     * microsecond precision of the DB timestamp round-trips exactly.
     */
    private record Cursor(Instant createdAt, UUID id) {

        static String encode(Instant createdAt, UUID id) {
            String raw = createdAt.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.lastIndexOf('|');
                return new Cursor(Instant.parse(raw.substring(0, separator)),
                        UUID.fromString(raw.substring(separator + 1)));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Malformed cursor", e);
            }
        }
    }
}

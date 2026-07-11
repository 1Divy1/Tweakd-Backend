package com.carsocialmedia.backend.notification.internal;

import com.carsocialmedia.backend.notification.NotificationService;
import com.carsocialmedia.backend.notification.dto.NotificationDto;
import com.carsocialmedia.backend.notification.dto.NotificationPageDto;
import com.carsocialmedia.backend.notification.exception.NotificationNotFoundException;
import com.carsocialmedia.backend.notification.internal.entities.NotificationEntity;
import com.carsocialmedia.backend.notification.internal.repositories.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void push(UUID userId, String type, String title, String body, Map<String, Object> payload) {
        notificationRepository.save(buildNotification(userId, type, title, body, payload));
    }

    @Override
    @Transactional
    public void pushToAll(Collection<UUID> userIds, String type, String title, String body, Map<String, Object> payload) {
        List<NotificationEntity> notifications = new LinkedHashSet<>(userIds).stream()
                .map(userId -> buildNotification(userId, type, title, body, payload))
                .toList();
        notificationRepository.saveAll(notifications);
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

        return new NotificationPageDto(page.stream().map(this::toDto).toList(), nextCursor);
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
        return new NotificationDto(
                entity.getId(),
                entity.getType(),
                entity.getTitle(),
                entity.getBody(),
                entity.getPayload(),
                entity.isRead(),
                entity.getCreatedAt());
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

package com.carsocialmedia.backend.dms.internal;

import com.carsocialmedia.backend.dms.DmsService;
import com.carsocialmedia.backend.dms.dto.DmConversationDto;
import com.carsocialmedia.backend.dms.dto.DmConversationPageDto;
import com.carsocialmedia.backend.dms.dto.DmMessageDto;
import com.carsocialmedia.backend.dms.dto.DmMessagePageDto;
import com.carsocialmedia.backend.dms.dto.DmReadReceiptDto;
import com.carsocialmedia.backend.dms.dto.SendMessageRequest;
import com.carsocialmedia.backend.dms.exception.CannotMessageSelfException;
import com.carsocialmedia.backend.dms.exception.DmConversationNotFoundException;
import com.carsocialmedia.backend.dms.exception.DmMessageNotFoundException;
import com.carsocialmedia.backend.dms.exception.DmRecipientNotFoundException;
import com.carsocialmedia.backend.dms.internal.entities.DmConversationEntity;
import com.carsocialmedia.backend.dms.internal.entities.DmMessageEntity;
import com.carsocialmedia.backend.dms.internal.entities.DmParticipantStateEntity;
import com.carsocialmedia.backend.dms.internal.events.DmConversationReadEvent;
import com.carsocialmedia.backend.dms.internal.events.DmMessageCreatedEvent;
import com.carsocialmedia.backend.dms.internal.events.DmMessageDeletedEvent;
import com.carsocialmedia.backend.dms.internal.repositories.DmConversationRepository;
import com.carsocialmedia.backend.dms.internal.repositories.DmMessageRepository;
import com.carsocialmedia.backend.dms.internal.repositories.DmParticipantStateRepository;
import com.carsocialmedia.backend.presence.PresenceService;
import com.carsocialmedia.backend.presence.dto.PresenceDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DmsServiceImpl implements DmsService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 140;

    private final DmConversationRepository conversationRepository;
    private final DmParticipantStateRepository stateRepository;
    private final DmMessageRepository messageRepository;
    private final ProfileService profileService;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final DmEventPusher eventPusher;

    public DmsServiceImpl(DmConversationRepository conversationRepository,
                          DmParticipantStateRepository stateRepository,
                          DmMessageRepository messageRepository,
                          ProfileService profileService,
                          PresenceService presenceService,
                          ApplicationEventPublisher eventPublisher,
                          DmEventPusher eventPusher) {
        this.conversationRepository = conversationRepository;
        this.stateRepository = stateRepository;
        this.messageRepository = messageRepository;
        this.profileService = profileService;
        this.presenceService = presenceService;
        this.eventPublisher = eventPublisher;
        this.eventPusher = eventPusher;
    }

    @Override
    @Transactional(readOnly = true)
    public DmConversationPageDto listConversations(UUID userId, String cursor, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        List<DmConversationEntity> rows;
        if (cursor == null) {
            rows = conversationRepository.findFirstPage(userId, limit);
        } else {
            Cursor decoded = Cursor.decode(cursor);
            rows = conversationRepository.findPageAfter(userId, decoded.timestamp(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<DmConversationEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? Cursor.encode(page.getLast().getLastMessageAt(), page.getLast().getId())
                : null;

        List<UUID> conversationIds = page.stream().map(DmConversationEntity::getId).toList();
        Map<UUID, Integer> unreadByConversation = stateRepository
                .findByConversationIdInAndUserId(conversationIds, userId).stream()
                .collect(Collectors.toMap(
                        DmParticipantStateEntity::getConversationId,
                        DmParticipantStateEntity::getUnreadCount));

        List<UUID> peerIds = page.stream().map(c -> c.peerOf(userId)).toList();
        Map<UUID, ProfileSearchResultDto> peers = profileService.findByIds(peerIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
        Map<UUID, PresenceDto> presence = presenceService.getPresence(peerIds);

        List<DmConversationDto> items = page.stream()
                .map(c -> {
                    PresenceDto peerPresence = presence.get(c.peerOf(userId));
                    return new DmConversationDto(
                            c.getId(),
                            peers.get(c.peerOf(userId)),
                            c.getLastMessagePreview(),
                            c.getLastMessageSenderId(),
                            c.getLastMessageAt(),
                            unreadByConversation.getOrDefault(c.getId(), 0),
                            peerPresence.online(),
                            peerPresence.lastSeenAt());
                })
                .toList();

        return new DmConversationPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public DmMessagePageDto listMessages(UUID userId, UUID conversationId, String cursor, int size) {
        DmConversationEntity conversation = requireParticipant(conversationId, userId);

        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        List<DmMessageEntity> rows;
        if (cursor == null) {
            rows = messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, limit);
        } else {
            Cursor decoded = Cursor.decode(cursor);
            rows = messageRepository.findPageAfter(conversationId, decoded.timestamp(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<DmMessageEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? Cursor.encode(page.getLast().getCreatedAt(), page.getLast().getId())
                : null;

        UUID peerLastRead = stateRepository
                .findByConversationIdAndUserId(conversationId, conversation.peerOf(userId))
                .map(DmParticipantStateEntity::getLastReadMessageId)
                .orElse(null);

        return new DmMessagePageDto(page.stream().map(this::toDto).toList(), nextCursor, peerLastRead);
    }

    @Override
    @Transactional
    public DmMessageDto sendMessage(UUID senderId, SendMessageRequest request) {
        UUID recipientId = request.recipientId();
        if (senderId.equals(recipientId)) {
            throw new CannotMessageSelfException();
        }
        if (profileService.findByIds(List.of(recipientId)).isEmpty()) {
            throw new DmRecipientNotFoundException(recipientId);
        }

        DmConversationEntity conversation = findOrCreateConversation(senderId, recipientId);

        DmMessageEntity message = new DmMessageEntity();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(request.content().strip());
        message.setCreatedAt(Instant.now());
        messageRepository.save(message);

        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setLastMessagePreview(preview(message.getContent()));
        conversation.setLastMessageSenderId(senderId);

        stateRepository.registerIncomingMessage(conversation.getId(), recipientId);
        stateRepository.unhide(conversation.getId(), senderId);

        DmMessageDto dto = toDto(message);
        eventPublisher.publishEvent(new DmMessageCreatedEvent(dto, recipientId, senderId));
        return dto;
    }

    @Override
    @Transactional
    public DmReadReceiptDto markRead(UUID userId, UUID conversationId) {
        DmConversationEntity conversation = requireParticipant(conversationId, userId);
        DmParticipantStateEntity state = stateRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new DmConversationNotFoundException(conversationId));

        UUID latestMessageId = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId)
                .map(DmMessageEntity::getId)
                .orElse(null);

        boolean watermarkMoved = latestMessageId != null
                && !latestMessageId.equals(state.getLastReadMessageId());
        state.setUnreadCount(0);
        if (watermarkMoved) {
            state.setLastReadMessageId(latestMessageId);
            eventPublisher.publishEvent(new DmConversationReadEvent(
                    conversationId, userId, conversation.peerOf(userId), latestMessageId));
        }

        return new DmReadReceiptDto(conversationId, state.getLastReadMessageId());
    }

    @Override
    @Transactional
    public void deleteMessage(UUID userId, UUID messageId) {
        DmMessageEntity message = messageRepository.findByIdAndSenderId(messageId, userId)
                .orElseThrow(() -> new DmMessageNotFoundException(messageId));
        if (message.isDeleted()) {
            return;
        }

        message.setDeleted(true);
        message.setContent("");

        // If this was the conversation's latest message, blank the chats-list preview too.
        DmConversationEntity conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new DmConversationNotFoundException(message.getConversationId()));
        if (Objects.equals(conversation.getLastMessageAt(), message.getCreatedAt())
                && Objects.equals(conversation.getLastMessageSenderId(), userId)) {
            conversation.setLastMessagePreview(null);
        }

        eventPublisher.publishEvent(new DmMessageDeletedEvent(
                conversation.getId(), messageId, conversation.getUserA(), conversation.getUserB()));
    }

    @Override
    @Transactional
    public void hideConversation(UUID userId, UUID conversationId) {
        DmParticipantStateEntity state = stateRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new DmConversationNotFoundException(conversationId));
        state.setHiddenAt(Instant.now());
        state.setUnreadCount(0); // the badge must not count invisible chats
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return stateRepository.sumUnread(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public void relayTyping(UUID userId, UUID conversationId, boolean typing) {
        // Silently drop instead of throwing: there is no HTTP response to carry an error, and a
        // misbehaving socket client must not learn which conversation ids exist.
        conversationRepository.findById(conversationId)
                .filter(c -> c.getUserA().equals(userId) || c.getUserB().equals(userId))
                .ifPresent(c -> eventPusher.pushTyping(conversationId, userId, c.peerOf(userId), typing));
    }

    /**
     * The participant check and the conversation fetch in one: a conversation the caller is not
     * part of is indistinguishable from one that does not exist.
     */
    private DmConversationEntity requireParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.findById(conversationId)
                .filter(c -> c.getUserA().equals(userId) || c.getUserB().equals(userId))
                .orElseThrow(() -> new DmConversationNotFoundException(conversationId));
    }

    /**
     * Canonicalizes the pair and finds or creates its conversation (plus both participant-state
     * rows). Creation goes through native ON CONFLICT upserts so two simultaneous first messages
     * cannot fail on the unique constraint.
     */
    private DmConversationEntity findOrCreateConversation(UUID senderId, UUID recipientId) {
        UUID userA = senderId.compareTo(recipientId) < 0 ? senderId : recipientId;
        UUID userB = senderId.compareTo(recipientId) < 0 ? recipientId : senderId;

        return conversationRepository.findByUserAAndUserB(userA, userB)
                .orElseGet(() -> {
                    conversationRepository.insertIgnoringConflict(UUID.randomUUID(), userA, userB);
                    DmConversationEntity conversation = conversationRepository
                            .findByUserAAndUserB(userA, userB)
                            .orElseThrow(); // just inserted (by us or the racing transaction)
                    stateRepository.insertIgnoringConflict(conversation.getId(), userA, userB);
                    return conversation;
                });
    }

    private String preview(String content) {
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH);
    }

    private DmMessageDto toDto(DmMessageEntity message) {
        return new DmMessageDto(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getContent(),
                message.isDeleted(),
                message.getCreatedAt());
    }

    /**
     * Keyset cursor over (timestamp, id) desc — same shape for the chats list (last_message_at) and
     * the message history (created_at). Encoded as base64url of "ISO-instant|uuid" so the
     * microsecond precision of the DB timestamp round-trips exactly.
     */
    private record Cursor(Instant timestamp, UUID id) {

        static String encode(Instant timestamp, UUID id) {
            String raw = timestamp.toString() + "|" + id;
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

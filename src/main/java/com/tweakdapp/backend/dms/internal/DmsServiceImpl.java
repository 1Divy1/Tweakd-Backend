package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.DmsService;
import com.tweakdapp.backend.dms.dto.DmConversationDto;
import com.tweakdapp.backend.dms.dto.DmConversationPageDto;
import com.tweakdapp.backend.dms.dto.DmMessageDto;
import com.tweakdapp.backend.dms.dto.DmMessagePageDto;
import com.tweakdapp.backend.dms.dto.DmReadReceiptDto;
import com.tweakdapp.backend.dms.dto.SendMessageRequest;
import com.tweakdapp.backend.dms.exception.CannotMessageSelfException;
import com.tweakdapp.backend.dms.exception.DmConversationNotFoundException;
import com.tweakdapp.backend.dms.exception.DmMessageNotFoundException;
import com.tweakdapp.backend.dms.exception.DmRecipientNotFoundException;
import com.tweakdapp.backend.dms.exception.EmptyMessageException;
import com.tweakdapp.backend.dms.exception.TaggedCarNotFoundException;
import com.tweakdapp.backend.dms.exception.TooManyTaggedCarsException;
import com.tweakdapp.backend.dms.internal.entities.DmConversationEntity;
import com.tweakdapp.backend.dms.internal.entities.DmMessageCarTagEntity;
import com.tweakdapp.backend.dms.internal.entities.DmMessageCarTagId;
import com.tweakdapp.backend.dms.internal.entities.DmMessageEntity;
import com.tweakdapp.backend.dms.internal.entities.DmParticipantStateEntity;
import com.tweakdapp.backend.dms.internal.events.DmConversationReadEvent;
import com.tweakdapp.backend.dms.internal.events.DmMessageCreatedEvent;
import com.tweakdapp.backend.dms.internal.events.DmMessageDeletedEvent;
import com.tweakdapp.backend.dms.internal.repositories.DmConversationRepository;
import com.tweakdapp.backend.dms.internal.repositories.DmMessageCarTagRepository;
import com.tweakdapp.backend.dms.internal.repositories.DmMessageRepository;
import com.tweakdapp.backend.dms.internal.repositories.DmParticipantStateRepository;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.presence.PresenceService;
import com.tweakdapp.backend.presence.dto.PresenceDto;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.tweakdapp.backend.shared.blocking.BlockDirectory;
import java.util.Collection;

@Service
public class DmsServiceImpl implements DmsService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 140;
    private static final int MAX_TAGGED_CARS = 10;

    private final DmConversationRepository conversationRepository;
    private final DmParticipantStateRepository stateRepository;
    private final DmMessageRepository messageRepository;
    private final DmMessageCarTagRepository carTagRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final DmEventPusher eventPusher;

    public DmsServiceImpl(DmConversationRepository conversationRepository,
                          DmParticipantStateRepository stateRepository,
                          DmMessageRepository messageRepository,
                          DmMessageCarTagRepository carTagRepository,
                          ProfileService profileService,
                          GarageService garageService,
                          PresenceService presenceService,
                          ApplicationEventPublisher eventPublisher,
                          DmEventPusher eventPusher) {
        this.conversationRepository = conversationRepository;
        this.stateRepository = stateRepository;
        this.messageRepository = messageRepository;
        this.carTagRepository = carTagRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.presenceService = presenceService;
        this.eventPublisher = eventPublisher;
        this.eventPusher = eventPusher;
    }

    @Override
    @Transactional(readOnly = true)
    public DmConversationPageDto listConversations(UUID userId, String cursor, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        // A conversation with someone a block separates from the caller stays out of the list, and
        // comes back untouched on unblock.
        Collection<UUID> hiddenIds = BlockDirectory.asQueryParam(profileService.findHiddenProfileIds(userId));
        List<DmConversationEntity> rows;
        if (cursor == null) {
            rows = conversationRepository.findFirstPage(userId, hiddenIds, limit);
        } else {
            Cursor decoded = Cursor.decode(cursor);
            rows = conversationRepository.findPageAfter(userId, decoded.timestamp(), decoded.id(), hiddenIds, limit);
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
                .map(c -> toConversationDto(
                        c,
                        peers.get(c.peerOf(userId)),
                        presence.get(c.peerOf(userId)),
                        unreadByConversation.getOrDefault(c.getId(), 0)))
                .toList();

        return new DmConversationPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public DmConversationDto getConversation(UUID userId, UUID conversationId) {
        DmConversationEntity conversation = requireParticipant(conversationId, userId);
        UUID peerId = conversation.peerOf(userId);

        int unread = stateRepository.findByConversationIdInAndUserId(List.of(conversationId), userId).stream()
                .findFirst()
                .map(DmParticipantStateEntity::getUnreadCount)
                .orElse(0);

        // Same two batch lookups the list uses, for one id each — keeps a single shape of row.
        ProfileSearchResultDto peer = profileService.findByIds(List.of(peerId)).stream()
                .findFirst()
                .orElse(null);

        return toConversationDto(conversation, peer, presenceService.getPresence(List.of(peerId)).get(peerId), unread);
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

        return new DmMessagePageDto(toDtos(page), nextCursor, peerLastRead);
    }

    @Override
    @Transactional
    public DmMessageDto sendMessage(UUID senderId, SendMessageRequest request) {
        UUID recipientId = request.recipientId();
        if (senderId.equals(recipientId)) {
            throw new CannotMessageSelfException();
        }

        // content is optional: a car-only message is valid, but an entirely empty one is not.
        String content = request.content() == null ? "" : request.content().strip();
        List<UUID> carIds = distinctIds(request.taggedCarIds());
        if (carIds.size() > MAX_TAGGED_CARS) {
            throw new TooManyTaggedCarsException(MAX_TAGGED_CARS);
        }
        if (content.isBlank() && carIds.isEmpty()) {
            throw new EmptyMessageException();
        }

        if (profileService.findByIds(List.of(recipientId)).isEmpty()) {
            throw new DmRecipientNotFoundException(recipientId);
        }
        // Resolve (and validate the existence of) the tagged cars before touching the DB.
        List<CarSummaryDto> taggedCars = resolveTaggedCars(carIds);

        DmConversationEntity conversation = findOrCreateConversation(senderId, recipientId);

        DmMessageEntity message = new DmMessageEntity();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(content); // dm_messages.content is NOT NULL; '' for a car-only message
        message.setCreatedAt(Instant.now());
        messageRepository.save(message);

        insertTaggedCars(message.getId(), carIds);

        conversation.setLastMessageAt(message.getCreatedAt());
        // The stored preview is the raw content; a blank preview on a non-deleted latest message
        // is the client's cue to render "shared cars".
        conversation.setLastMessagePreview(preview(content));
        conversation.setLastMessageSenderId(senderId);

        stateRepository.registerIncomingMessage(conversation.getId(), recipientId);
        stateRepository.unhide(conversation.getId(), senderId);

        DmMessageDto dto = toDto(message, taggedCars);
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
                .filter(c -> !profileService.isHiddenFrom(userId, c.peerOf(userId)))
                .ifPresent(c -> eventPusher.pushTyping(conversationId, userId, c.peerOf(userId), typing));
    }

    /**
     * The participant check and the conversation fetch in one: a conversation the caller is not
     * part of is indistinguishable from one that does not exist.
     */
    /**
     * One chats-list row. Shared by the list and the single-conversation read so the two can never
     * drift into describing the same conversation differently.
     *
     * <p>{@code peer} is null only if the peer's profile could not be resolved (a hard-deleted
     * account); the row is still returned, as the list has always done.
     */
    private static DmConversationDto toConversationDto(DmConversationEntity conversation,
                                                       ProfileSearchResultDto peer,
                                                       PresenceDto peerPresence,
                                                       int unreadCount) {
        return new DmConversationDto(
                conversation.getId(),
                peer,
                conversation.getLastMessagePreview(),
                conversation.getLastMessageSenderId(),
                conversation.getLastMessageAt(),
                unreadCount,
                peerPresence.online(),
                peerPresence.lastSeenAt());
    }

    private DmConversationEntity requireParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.findById(conversationId)
                .filter(c -> c.getUserA().equals(userId) || c.getUserB().equals(userId))
                .filter(c -> !profileService.isHiddenFrom(userId, c.peerOf(userId)))
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

    /**
     * Assembles a whole page of messages with a fixed, small number of queries regardless of page
     * size (no N+1): one batch load of the page's car-tag rows and one
     * {@code garageService.findCarsByIds} covering every tagged car. Deleted messages carry no
     * tags (same rule as their blanked content), so they never enter the lookup.
     */
    private List<DmMessageDto> toDtos(List<DmMessageEntity> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        List<UUID> liveMessageIds = messages.stream()
                .filter(m -> !m.isDeleted())
                .map(DmMessageEntity::getId)
                .toList();
        Map<UUID, List<UUID>> carIdsByMessage = liveMessageIds.isEmpty()
                ? Map.of()
                : carTagRepository.findAllByIdMessageIdIn(liveMessageIds).stream()
                        .collect(Collectors.groupingBy(t -> t.getId().getMessageId(),
                                Collectors.mapping(t -> t.getId().getCarId(), Collectors.toList())));

        Set<UUID> allCarIds = new HashSet<>();
        carIdsByMessage.values().forEach(allCarIds::addAll);
        Map<UUID, CarSummaryDto> cars = allCarIds.isEmpty()
                ? Map.of()
                : garageService.findCarsByIds(allCarIds).stream()
                        .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

        return messages.stream()
                .map(m -> {
                    List<CarSummaryDto> taggedCars = m.isDeleted()
                            ? List.of()
                            : carIdsByMessage.getOrDefault(m.getId(), List.of()).stream()
                                    .map(cars::get)
                                    .filter(Objects::nonNull)
                                    .toList();
                    return toDto(m, taggedCars);
                })
                .toList();
    }

    private DmMessageDto toDto(DmMessageEntity message, List<CarSummaryDto> taggedCars) {
        return new DmMessageDto(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getContent(),
                message.isDeleted(),
                message.getCreatedAt(),
                taggedCars);
    }

    /**
     * Resolves the tagged cars through the garage public API, preserving the requested order, and
     * fails the whole send if any car id is unknown. Any user's car may be tagged (owners are
     * neither tagged nor notified).
     */
    private List<CarSummaryDto> resolveTaggedCars(List<UUID> carIds) {
        if (carIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, CarSummaryDto> byId = garageService.findCarsByIds(carIds).stream()
                .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));
        if (byId.size() != carIds.size()) {
            throw new TaggedCarNotFoundException();
        }
        return carIds.stream().map(byId::get).toList();
    }

    private void insertTaggedCars(UUID messageId, List<UUID> carIds) {
        for (UUID carId : carIds) {
            DmMessageCarTagEntity tag = new DmMessageCarTagEntity();
            tag.setId(new DmMessageCarTagId(messageId, carId));
            carTagRepository.save(tag);
        }
    }

    private static List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
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

package com.tweakdapp.backend.dms.internal;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Business rules of {@link DmsServiceImpl}: send (self/recipient guards, conversation create/dedup,
 * content strip + preview truncation, unread bump/unhide), read watermark advancement + event
 * suppression when nothing moves, sender-scoped idempotent soft delete + preview blanking, hide,
 * typing participant gating, and the chats-list / history keyset mapping — all with mocked
 * collaborators.
 */
class DmsServiceImplTest {

    // 001 < 002 canonical order, so userA = SELF, userB = PEER for this pair.
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PEER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONV = UUID.fromString("00000000-0000-0000-0000-0000000000c0");

    private DmConversationRepository conversationRepository;
    private DmParticipantStateRepository stateRepository;
    private DmMessageRepository messageRepository;
    private DmMessageCarTagRepository carTagRepository;
    private ProfileService profileService;
    private GarageService garageService;
    private PresenceService presenceService;
    private ApplicationEventPublisher eventPublisher;
    private DmEventPusher eventPusher;

    private DmsServiceImpl service;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(DmConversationRepository.class);
        stateRepository = mock(DmParticipantStateRepository.class);
        messageRepository = mock(DmMessageRepository.class);
        carTagRepository = mock(DmMessageCarTagRepository.class);
        profileService = mock(ProfileService.class);
        garageService = mock(GarageService.class);
        presenceService = mock(PresenceService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        eventPusher = mock(DmEventPusher.class);
        service = new DmsServiceImpl(conversationRepository, stateRepository, messageRepository,
                carTagRepository, profileService, garageService, presenceService, eventPublisher, eventPusher);
    }

    // ---- helpers ------------------------------------------------------------

    private static DmConversationEntity conversation(UUID id, UUID userA, UUID userB) {
        DmConversationEntity c = new DmConversationEntity();
        c.setId(id);
        c.setUserA(userA);
        c.setUserB(userB);
        return c;
    }

    private static DmMessageEntity message(UUID id, UUID conversationId, UUID senderId, Instant createdAt) {
        DmMessageEntity m = new DmMessageEntity();
        m.setId(id);
        m.setConversationId(conversationId);
        m.setSenderId(senderId);
        m.setContent("hi");
        m.setCreatedAt(createdAt);
        return m;
    }

    private static DmParticipantStateEntity state(UUID conversationId, UUID userId) {
        DmParticipantStateEntity s = new DmParticipantStateEntity();
        s.setConversationId(conversationId);
        s.setUserId(userId);
        return s;
    }

    private void recipientExists() {
        when(profileService.findByIds(List.of(PEER)))
                .thenReturn(List.of(new ProfileSearchResultDto(PEER, "Peer Name", "peer", null)));
    }

    private static CarSummaryDto car(UUID id) {
        return new CarSummaryDto(id, "BMW", "M3", null, null, null);
    }

    private static DmMessageCarTagEntity tag(UUID messageId, UUID carId) {
        DmMessageCarTagEntity t = new DmMessageCarTagEntity();
        t.setId(new DmMessageCarTagId(messageId, carId));
        return t;
    }

    // ---- sendMessage --------------------------------------------------------

    @Test
    void sendingToYourselfIsRejectedBeforeAnyPersistence() {
        assertThatExceptionOfType(CannotMessageSelfException.class)
                .isThrownBy(() -> service.sendMessage(SELF, new SendMessageRequest(SELF, "hey", null)));

        verifyNoInteractions(conversationRepository, messageRepository, stateRepository);
    }

    @Test
    void sendingToAnUnknownRecipientIsRejected() {
        when(profileService.findByIds(List.of(PEER))).thenReturn(List.of());

        assertThatExceptionOfType(DmRecipientNotFoundException.class)
                .isThrownBy(() -> service.sendMessage(SELF, new SendMessageRequest(PEER, "hey", null)));

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    void sendingPersistsStrippedContentBumpsRecipientUnhidesSenderAndPublishesCreatedEvent() {
        recipientExists();
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findByUserAAndUserB(SELF, PEER)).thenReturn(Optional.of(conv));

        DmMessageDto dto = service.sendMessage(SELF, new SendMessageRequest(PEER, "   hello world   ", null));

        assertThat(dto.senderId()).isEqualTo(SELF);
        assertThat(dto.conversationId()).isEqualTo(CONV);
        assertThat(dto.content()).isEqualTo("hello world");
        assertThat(dto.deleted()).isFalse();

        ArgumentCaptor<DmMessageEntity> saved = ArgumentCaptor.forClass(DmMessageEntity.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("hello world");
        assertThat(saved.getValue().getSenderId()).isEqualTo(SELF);

        // Denormalized chats-list fields updated on the conversation.
        assertThat(conv.getLastMessagePreview()).isEqualTo("hello world");
        assertThat(conv.getLastMessageSenderId()).isEqualTo(SELF);
        assertThat(conv.getLastMessageAt()).isEqualTo(dto.createdAt());

        verify(stateRepository).registerIncomingMessage(CONV, PEER);
        verify(stateRepository).unhide(CONV, SELF);

        ArgumentCaptor<DmMessageCreatedEvent> event = ArgumentCaptor.forClass(DmMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().recipientId()).isEqualTo(PEER);
        assertThat(event.getValue().senderId()).isEqualTo(SELF);
        assertThat(event.getValue().message()).isEqualTo(dto);
    }

    @Test
    void previewIsTruncatedToOneHundredFortyCharactersButFullContentIsStored() {
        recipientExists();
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findByUserAAndUserB(SELF, PEER)).thenReturn(Optional.of(conv));
        String longText = "x".repeat(200);

        DmMessageDto dto = service.sendMessage(SELF, new SendMessageRequest(PEER, longText, null));

        assertThat(dto.content()).hasSize(200);
        assertThat(conv.getLastMessagePreview()).hasSize(140);
    }

    @Test
    void firstMessageOfAPairCreatesTheConversationAndBothStateRowsViaUpserts() {
        recipientExists();
        DmConversationEntity created = conversation(CONV, SELF, PEER);
        // Absent on the first look, present after the ON CONFLICT insert.
        when(conversationRepository.findByUserAAndUserB(SELF, PEER))
                .thenReturn(Optional.empty(), Optional.of(created));

        service.sendMessage(SELF, new SendMessageRequest(PEER, "first", null));

        verify(conversationRepository).insertIgnoringConflict(any(UUID.class), eq(SELF), eq(PEER));
        verify(stateRepository).insertIgnoringConflict(CONV, SELF, PEER);
    }

    @Test
    void conversationPairIsCanonicalizedRegardlessOfSenderOrder() {
        // Sender is the higher UUID; the pair must still be stored as (PEER=002 lower? no) — verify
        // the arguments passed are min(sender,recipient), max(sender,recipient).
        when(profileService.findByIds(List.of(SELF))).thenReturn(List.of(new ProfileSearchResultDto(SELF, "S Name", "s", null)));
        when(conversationRepository.findByUserAAndUserB(SELF, PEER))
                .thenReturn(Optional.of(conversation(CONV, SELF, PEER)));

        // PEER (002) sends to SELF (001): userA must be 001, userB 002.
        service.sendMessage(PEER, new SendMessageRequest(SELF, "hi", null));

        verify(conversationRepository).findByUserAAndUserB(SELF, PEER);
    }

    // ---- sendMessage: car tagging -------------------------------------------

    private static final UUID CAR1 = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
    private static final UUID CAR2 = UUID.fromString("00000000-0000-0000-0000-0000000000cb");
    private static final UUID CAR3 = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @Test
    void sendingWithTextAndTaggedCarsPersistsTagsInOrderAndPublishesThemOnTheEvent() {
        recipientExists();
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findByUserAAndUserB(SELF, PEER)).thenReturn(Optional.of(conv));
        // garage returns them unordered; the service must re-order to the request order.
        when(garageService.findCarsByIds(List.of(CAR1, CAR2))).thenReturn(List.of(car(CAR2), car(CAR1)));

        DmMessageDto dto = service.sendMessage(SELF,
                new SendMessageRequest(PEER, "look at these", List.of(CAR1, CAR2)));

        assertThat(dto.content()).isEqualTo("look at these");
        assertThat(dto.taggedCars()).extracting(CarSummaryDto::id).containsExactly(CAR1, CAR2);

        ArgumentCaptor<DmMessageCarTagEntity> tags = ArgumentCaptor.forClass(DmMessageCarTagEntity.class);
        verify(carTagRepository, times(2)).save(tags.capture());
        assertThat(tags.getAllValues()).extracting(t -> t.getId().getCarId()).containsExactly(CAR1, CAR2);

        ArgumentCaptor<DmMessageCreatedEvent> event = ArgumentCaptor.forClass(DmMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().message().taggedCars()).extracting(CarSummaryDto::id)
                .containsExactly(CAR1, CAR2);
    }

    @Test
    void sendingCarsOnlyWithNullContentStoresEmptyStringAndABlankPreview() {
        recipientExists();
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findByUserAAndUserB(SELF, PEER)).thenReturn(Optional.of(conv));
        when(garageService.findCarsByIds(List.of(CAR1))).thenReturn(List.of(car(CAR1)));

        DmMessageDto dto = service.sendMessage(SELF, new SendMessageRequest(PEER, null, List.of(CAR1)));

        assertThat(dto.content()).isEmpty();
        assertThat(dto.taggedCars()).extracting(CarSummaryDto::id).containsExactly(CAR1);

        ArgumentCaptor<DmMessageEntity> saved = ArgumentCaptor.forClass(DmMessageEntity.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEmpty();
        // blank preview on a non-deleted latest message = the client's "shared cars" cue.
        assertThat(conv.getLastMessagePreview()).isEmpty();
    }

    @Test
    void sendingWithNeitherTextNorCarsIsRejectedBeforeAnyPersistence() {
        assertThatExceptionOfType(EmptyMessageException.class)
                .isThrownBy(() -> service.sendMessage(SELF, new SendMessageRequest(PEER, "   ", null)));

        verifyNoInteractions(conversationRepository, messageRepository, carTagRepository, garageService, profileService);
    }

    @Test
    void sendingWithMoreThanTenTaggedCarsIsRejected() {
        List<UUID> eleven = java.util.stream.Stream.generate(UUID::randomUUID).limit(11).toList();

        assertThatExceptionOfType(TooManyTaggedCarsException.class)
                .isThrownBy(() -> service.sendMessage(SELF, new SendMessageRequest(PEER, "hi", eleven)));

        verifyNoInteractions(conversationRepository, messageRepository, carTagRepository, garageService);
    }

    @Test
    void duplicateTaggedCarIdsAreDeduplicated() {
        recipientExists();
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findByUserAAndUserB(SELF, PEER)).thenReturn(Optional.of(conv));
        when(garageService.findCarsByIds(List.of(CAR1))).thenReturn(List.of(car(CAR1)));

        DmMessageDto dto = service.sendMessage(SELF, new SendMessageRequest(PEER, "hi", List.of(CAR1, CAR1, CAR1)));

        verify(garageService).findCarsByIds(List.of(CAR1)); // collapsed to one distinct id
        verify(carTagRepository, times(1)).save(any());
        assertThat(dto.taggedCars()).extracting(CarSummaryDto::id).containsExactly(CAR1);
    }

    @Test
    void sendingWithAnUnknownTaggedCarIsRejectedBeforePersistence() {
        recipientExists();
        // Only CAR1 resolves; CAR2 is unknown.
        when(garageService.findCarsByIds(List.of(CAR1, CAR2))).thenReturn(List.of(car(CAR1)));

        assertThatExceptionOfType(TaggedCarNotFoundException.class)
                .isThrownBy(() -> service.sendMessage(SELF, new SendMessageRequest(PEER, "hi", List.of(CAR1, CAR2))));

        verifyNoInteractions(conversationRepository, messageRepository, carTagRepository);
    }

    // ---- listMessages: car tagging assembly ---------------------------------

    @Test
    void listMessagesAssemblesTaggedCarsForThePageWithASingleBatchLookup() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));
        UUID m1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID m2 = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        DmMessageEntity msg1 = message(m1, CONV, SELF, Instant.parse("2026-07-16T10:00:00Z"));
        DmMessageEntity msg2 = message(m2, CONV, PEER, Instant.parse("2026-07-16T09:00:00Z"));
        when(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(eq(CONV), any()))
                .thenReturn(List.of(msg1, msg2));
        when(carTagRepository.findAllByIdMessageIdIn(List.of(m1, m2)))
                .thenReturn(List.of(tag(m1, CAR1), tag(m1, CAR2), tag(m2, CAR3)));
        when(garageService.findCarsByIds(any())).thenReturn(List.of(car(CAR1), car(CAR2), car(CAR3)));

        DmMessagePageDto page = service.listMessages(SELF, CONV, null, 30);

        // Exactly one tag query and one car lookup for the whole page (no N+1).
        verify(carTagRepository, times(1)).findAllByIdMessageIdIn(List.of(m1, m2));
        verify(garageService, times(1)).findCarsByIds(any());
        assertThat(page.items().get(0).taggedCars()).extracting(CarSummaryDto::id).containsExactly(CAR1, CAR2);
        assertThat(page.items().get(1).taggedCars()).extracting(CarSummaryDto::id).containsExactly(CAR3);
    }

    @Test
    void deletedMessagesReturnEmptyTaggedCarsAndAreExcludedFromTheTagLookup() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));
        UUID mLive = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID mDeleted = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        DmMessageEntity live = message(mLive, CONV, SELF, Instant.parse("2026-07-16T10:00:00Z"));
        DmMessageEntity deleted = message(mDeleted, CONV, SELF, Instant.parse("2026-07-16T09:00:00Z"));
        deleted.setDeleted(true);
        when(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(eq(CONV), any()))
                .thenReturn(List.of(live, deleted));
        // Only the live message id is queried; a deleted message never exposes its tags.
        when(carTagRepository.findAllByIdMessageIdIn(List.of(mLive))).thenReturn(List.of(tag(mLive, CAR1)));
        when(garageService.findCarsByIds(any())).thenReturn(List.of(car(CAR1)));

        DmMessagePageDto page = service.listMessages(SELF, CONV, null, 30);

        verify(carTagRepository).findAllByIdMessageIdIn(List.of(mLive));
        DmMessageDto liveDto = page.items().stream().filter(d -> !d.deleted()).findFirst().orElseThrow();
        DmMessageDto deletedDto = page.items().stream().filter(DmMessageDto::deleted).findFirst().orElseThrow();
        assertThat(liveDto.taggedCars()).extracting(CarSummaryDto::id).containsExactly(CAR1);
        assertThat(deletedDto.taggedCars()).isEmpty();
    }

    // ---- markRead -----------------------------------------------------------

    @Test
    void markReadOnANonParticipantConversationIs404() {
        when(conversationRepository.findById(CONV)).thenReturn(Optional.empty());

        assertThatExceptionOfType(DmConversationNotFoundException.class)
                .isThrownBy(() -> service.markRead(SELF, CONV));
    }

    @Test
    void markReadAdvancesTheWatermarkZeroesUnreadAndPublishesReadEvent() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));
        DmParticipantStateEntity st = state(CONV, SELF);
        st.setUnreadCount(4);
        st.setLastReadMessageId(null);
        when(stateRepository.findByConversationIdAndUserId(CONV, SELF)).thenReturn(Optional.of(st));
        UUID latest = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(CONV))
                .thenReturn(Optional.of(message(latest, CONV, PEER, Instant.now())));

        DmReadReceiptDto receipt = service.markRead(SELF, CONV);

        assertThat(st.getUnreadCount()).isZero();
        assertThat(st.getLastReadMessageId()).isEqualTo(latest);
        assertThat(receipt.lastReadMessageId()).isEqualTo(latest);

        ArgumentCaptor<DmConversationReadEvent> event = ArgumentCaptor.forClass(DmConversationReadEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().readerId()).isEqualTo(SELF);
        assertThat(event.getValue().peerId()).isEqualTo(PEER);
        assertThat(event.getValue().lastReadMessageId()).isEqualTo(latest);
    }

    @Test
    void markReadWhenAlreadyAtLatestZeroesUnreadButPublishesNoEvent() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));
        UUID latest = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
        DmParticipantStateEntity st = state(CONV, SELF);
        st.setUnreadCount(2);
        st.setLastReadMessageId(latest);
        when(stateRepository.findByConversationIdAndUserId(CONV, SELF)).thenReturn(Optional.of(st));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(CONV))
                .thenReturn(Optional.of(message(latest, CONV, PEER, Instant.now())));

        DmReadReceiptDto receipt = service.markRead(SELF, CONV);

        assertThat(st.getUnreadCount()).isZero();
        assertThat(receipt.lastReadMessageId()).isEqualTo(latest);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markReadOnAnEmptyConversationZeroesUnreadAndKeepsNullWatermark() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));
        DmParticipantStateEntity st = state(CONV, SELF);
        st.setUnreadCount(0);
        when(stateRepository.findByConversationIdAndUserId(CONV, SELF)).thenReturn(Optional.of(st));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(CONV))
                .thenReturn(Optional.empty());

        DmReadReceiptDto receipt = service.markRead(SELF, CONV);

        assertThat(receipt.lastReadMessageId()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- deleteMessage ------------------------------------------------------

    @Test
    void deletingAMessageThatIsNotYoursIs404() {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        when(messageRepository.findByIdAndSenderId(msgId, SELF)).thenReturn(Optional.empty());

        assertThatExceptionOfType(DmMessageNotFoundException.class)
                .isThrownBy(() -> service.deleteMessage(SELF, msgId));
    }

    @Test
    void deletingAnAlreadyDeletedMessageIsANoOp() {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        DmMessageEntity m = message(msgId, CONV, SELF, Instant.now());
        m.setDeleted(true);
        when(messageRepository.findByIdAndSenderId(msgId, SELF)).thenReturn(Optional.of(m));

        service.deleteMessage(SELF, msgId);

        verify(eventPublisher, never()).publishEvent(any());
        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void deletingTheLatestMessageBlanksItAndClearsTheChatsListPreview() {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        Instant at = Instant.parse("2026-07-16T10:00:00Z");
        DmMessageEntity m = message(msgId, CONV, SELF, at);
        m.setContent("secret");
        when(messageRepository.findByIdAndSenderId(msgId, SELF)).thenReturn(Optional.of(m));
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        conv.setLastMessageAt(at);
        conv.setLastMessageSenderId(SELF);
        conv.setLastMessagePreview("secret");
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));

        service.deleteMessage(SELF, msgId);

        assertThat(m.isDeleted()).isTrue();
        assertThat(m.getContent()).isEmpty();
        assertThat(conv.getLastMessagePreview()).isNull();

        ArgumentCaptor<DmMessageDeletedEvent> event = ArgumentCaptor.forClass(DmMessageDeletedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().messageId()).isEqualTo(msgId);
        assertThat(event.getValue().userA()).isEqualTo(SELF);
        assertThat(event.getValue().userB()).isEqualTo(PEER);
    }

    @Test
    void deletingAnOlderMessageLeavesTheChatsListPreviewIntact() {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        Instant messageAt = Instant.parse("2026-07-16T09:00:00Z");
        Instant latestAt = Instant.parse("2026-07-16T10:00:00Z");
        DmMessageEntity m = message(msgId, CONV, SELF, messageAt);
        when(messageRepository.findByIdAndSenderId(msgId, SELF)).thenReturn(Optional.of(m));
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        conv.setLastMessageAt(latestAt); // a newer message is the current preview
        conv.setLastMessageSenderId(SELF);
        conv.setLastMessagePreview("newer message");
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));

        service.deleteMessage(SELF, msgId);

        assertThat(conv.getLastMessagePreview()).isEqualTo("newer message");
        verify(eventPublisher).publishEvent(any(DmMessageDeletedEvent.class));
    }

    // ---- hideConversation ---------------------------------------------------

    @Test
    void hidingANonParticipantConversationIs404() {
        when(stateRepository.findByConversationIdAndUserId(CONV, SELF)).thenReturn(Optional.empty());

        assertThatExceptionOfType(DmConversationNotFoundException.class)
                .isThrownBy(() -> service.hideConversation(SELF, CONV));
    }

    @Test
    void hidingAConversationSetsHiddenAtAndZeroesUnread() {
        DmParticipantStateEntity st = state(CONV, SELF);
        st.setUnreadCount(3);
        when(stateRepository.findByConversationIdAndUserId(CONV, SELF)).thenReturn(Optional.of(st));

        service.hideConversation(SELF, CONV);

        assertThat(st.getHiddenAt()).isNotNull();
        assertThat(st.getUnreadCount()).isZero();
    }

    // ---- countUnread --------------------------------------------------------

    @Test
    void countUnreadDelegatesToTheAggregateQuery() {
        when(stateRepository.sumUnread(SELF)).thenReturn(7L);

        assertThat(service.countUnread(SELF)).isEqualTo(7L);
    }

    // ---- relayTyping --------------------------------------------------------

    @Test
    void typingIsRelayedToThePeerWhenTheCallerParticipates() {
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conversation(CONV, SELF, PEER)));

        service.relayTyping(SELF, CONV, true);

        verify(eventPusher).pushTyping(CONV, SELF, PEER, true);
    }

    @Test
    void typingIsSilentlyDroppedWhenTheCallerIsNotAParticipant() {
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conversation(CONV, SELF, PEER)));

        service.relayTyping(stranger, CONV, true);

        verifyNoInteractions(eventPusher);
    }

    @Test
    void typingIsSilentlyDroppedWhenTheConversationDoesNotExist() {
        when(conversationRepository.findById(CONV)).thenReturn(Optional.empty());

        service.relayTyping(SELF, CONV, true);

        verifyNoInteractions(eventPusher);
    }

    // ---- listConversations --------------------------------------------------

    @Test
    void listConversationsMapsPeerUnreadAndPresenceAndHasNoCursorOnTheLastPage() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        conv.setLastMessagePreview("yo");
        conv.setLastMessageSenderId(PEER);
        conv.setLastMessageAt(Instant.parse("2026-07-16T10:00:00Z"));
        when(conversationRepository.findFirstPage(eq(SELF), any(Pageable.class))).thenReturn(List.of(conv));

        DmParticipantStateEntity st = state(CONV, SELF);
        st.setUnreadCount(5);
        when(stateRepository.findByConversationIdInAndUserId(List.of(CONV), SELF)).thenReturn(List.of(st));
        when(profileService.findByIds(List.of(PEER)))
                .thenReturn(List.of(new ProfileSearchResultDto(PEER, "Peer Name", "peer", "a.png")));
        when(presenceService.getPresence(List.of(PEER)))
                .thenReturn(Map.of(PEER, PresenceDto.online(PEER)));

        DmConversationPageDto page = service.listConversations(SELF, null, 20);

        assertThat(page.nextCursor()).isNull();
        assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.id()).isEqualTo(CONV);
        assertThat(item.peer().username()).isEqualTo("peer");
        assertThat(item.lastMessagePreview()).isEqualTo("yo");
        assertThat(item.unreadCount()).isEqualTo(5);
        assertThat(item.peerOnline()).isTrue();
    }

    @Test
    void listConversationsTrimsToPageSizeAndEmitsACursorWhenMoreRowsExist() {
        DmConversationEntity first = conversation(CONV, SELF, PEER);
        first.setLastMessageAt(Instant.parse("2026-07-16T10:00:00Z"));
        UUID conv2Id = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID peer2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        DmConversationEntity second = conversation(conv2Id, SELF, peer2);
        second.setLastMessageAt(Instant.parse("2026-07-16T09:00:00Z"));
        // Repo returns size+1 rows (2) for a requested size of 1 → hasMore.
        when(conversationRepository.findFirstPage(eq(SELF), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(stateRepository.findByConversationIdInAndUserId(List.of(CONV), SELF)).thenReturn(List.of());
        when(profileService.findByIds(List.of(PEER)))
                .thenReturn(List.of(new ProfileSearchResultDto(PEER, "Peer Name", "peer", null)));
        when(presenceService.getPresence(List.of(PEER)))
                .thenReturn(Map.of(PEER, PresenceDto.offline(PEER, null)));

        DmConversationPageDto page = service.listConversations(SELF, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo(CONV);
        assertThat(page.nextCursor()).isNotBlank();
        assertThat(page.items().getFirst().unreadCount()).isZero(); // absent state → default 0
    }

    @Test
    void listConversationsClampsAnOversizedPageRequest() {
        when(conversationRepository.findFirstPage(eq(SELF), any(Pageable.class))).thenReturn(List.of());

        service.listConversations(SELF, null, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(conversationRepository).findFirstPage(eq(SELF), pageable.capture());
        // clamp(1000, 1, 50) = 50, plus the +1 look-ahead row.
        assertThat(pageable.getValue().getPageSize()).isEqualTo(51);
    }

    // ---- listMessages -------------------------------------------------------

    @Test
    void listMessagesOnANonParticipantConversationIs404() {
        when(conversationRepository.findById(CONV)).thenReturn(Optional.empty());

        assertThatExceptionOfType(DmConversationNotFoundException.class)
                .isThrownBy(() -> service.listMessages(SELF, CONV, null, 30));
    }

    @Test
    void listMessagesReturnsNewestFirstPageWithThePeersReadWatermark() {
        DmConversationEntity conv = conversation(CONV, SELF, PEER);
        when(conversationRepository.findById(CONV)).thenReturn(Optional.of(conv));
        UUID m1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        when(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(eq(CONV), any(Pageable.class)))
                .thenReturn(List.of(message(m1, CONV, SELF, Instant.now())));
        UUID peerWatermark = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        DmParticipantStateEntity peerState = state(CONV, PEER);
        peerState.setLastReadMessageId(peerWatermark);
        when(stateRepository.findByConversationIdAndUserId(CONV, PEER)).thenReturn(Optional.of(peerState));

        DmMessagePageDto page = service.listMessages(SELF, CONV, null, 30);

        assertThat(page.items()).extracting(DmMessageDto::id).containsExactly(m1);
        assertThat(page.peerLastReadMessageId()).isEqualTo(peerWatermark);
        assertThat(page.nextCursor()).isNull();
        verifyNoMoreInteractions(profileService);
    }
}

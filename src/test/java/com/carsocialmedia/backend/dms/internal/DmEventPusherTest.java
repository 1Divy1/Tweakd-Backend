package com.carsocialmedia.backend.dms.internal;

import com.carsocialmedia.backend.dms.dto.DmMessageDto;
import com.carsocialmedia.backend.dms.dto.DmSocketEvent;
import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.dms.internal.events.DmConversationReadEvent;
import com.carsocialmedia.backend.dms.internal.events.DmMessageCreatedEvent;
import com.carsocialmedia.backend.dms.internal.events.DmMessageDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The single WebSocket egress point ({@link DmEventPusher}): every DM event fans out to the right
 * users' {@code /user/queue/dms} destination with the correctly discriminated {@link DmSocketEvent}
 * payload — created events reach recipient + sender, deletions reach both participants, read
 * receipts reach peer + reader, typing reaches only the peer, presence reaches every shared peer.
 */
class DmEventPusherTest {

    private static final String QUEUE = "/queue/dms";
    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONV = UUID.fromString("00000000-0000-0000-0000-0000000000c0");

    private SimpMessagingTemplate messagingTemplate;
    private DmEventPusher pusher;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        pusher = new DmEventPusher(messagingTemplate);
    }

    private ArgumentCaptor<Object> captureSends(int expectedCount) {
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(expectedCount))
                .convertAndSendToUser(user.capture(), eq(QUEUE), payload.capture());
        this.capturedUsers = user;
        return payload;
    }

    private ArgumentCaptor<String> capturedUsers;

    @Test
    void messageCreatedIsPushedToBothTheRecipientAndTheSendersOtherDevicesCarryingTaggedCars() {
        CarSummaryDto car = new CarSummaryDto(
                UUID.fromString("00000000-0000-0000-0000-0000000000ca"), "BMW", "M3", null, null, null);
        DmMessageDto message = new DmMessageDto(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), CONV, SENDER, "hi", false,
                Instant.now(), List.of(car));

        pusher.on(new DmMessageCreatedEvent(message, RECIPIENT, SENDER));

        ArgumentCaptor<Object> payloads = captureSends(2);
        assertThat(capturedUsers.getAllValues())
                .containsExactlyInAnyOrder(RECIPIENT.toString(), SENDER.toString());
        assertThat(payloads.getAllValues()).allSatisfy(p -> {
            DmSocketEvent event = (DmSocketEvent) p;
            assertThat(event.type()).isEqualTo("message.created");
            assertThat(event.message()).isEqualTo(message);
            assertThat(event.message().taggedCars()).containsExactly(car);
            assertThat(event.conversationId()).isEqualTo(CONV);
        });
    }

    @Test
    void messageDeletedIsPushedToBothParticipants() {
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

        pusher.on(new DmMessageDeletedEvent(CONV, messageId, SENDER, RECIPIENT));

        ArgumentCaptor<Object> payloads = captureSends(2);
        assertThat(capturedUsers.getAllValues())
                .containsExactlyInAnyOrder(SENDER.toString(), RECIPIENT.toString());
        DmSocketEvent event = (DmSocketEvent) payloads.getValue();
        assertThat(event.type()).isEqualTo("message.deleted");
        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.conversationId()).isEqualTo(CONV);
    }

    @Test
    void conversationReadIsPushedToThePeerAndTheReadersOtherDevices() {
        UUID watermark = UUID.fromString("00000000-0000-0000-0000-0000000000a9");

        pusher.on(new DmConversationReadEvent(CONV, RECIPIENT, SENDER, watermark));

        ArgumentCaptor<Object> payloads = captureSends(2);
        assertThat(capturedUsers.getAllValues())
                .containsExactlyInAnyOrder(SENDER.toString(), RECIPIENT.toString());
        DmSocketEvent event = (DmSocketEvent) payloads.getValue();
        assertThat(event.type()).isEqualTo("conversation.read");
        assertThat(event.userId()).isEqualTo(RECIPIENT); // the reader
        assertThat(event.lastReadMessageId()).isEqualTo(watermark);
    }

    @Test
    void typingIsPushedOnlyToThePeer() {
        pusher.pushTyping(CONV, SENDER, RECIPIENT, true);

        ArgumentCaptor<Object> payloads = captureSends(1);
        assertThat(capturedUsers.getValue()).isEqualTo(RECIPIENT.toString());
        DmSocketEvent event = (DmSocketEvent) payloads.getValue();
        assertThat(event.type()).isEqualTo("typing");
        assertThat(event.userId()).isEqualTo(SENDER); // who is typing
        assertThat(event.typing()).isTrue();
        assertThat(event.conversationId()).isEqualTo(CONV);
    }

    @Test
    void presenceIsPushedToEveryPeerSharingAConversation() {
        UUID peer1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID peer2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Instant lastSeen = Instant.parse("2026-07-16T10:00:00Z");

        pusher.pushPresence(List.of(peer1, peer2), SENDER, false, lastSeen);

        ArgumentCaptor<Object> payloads = captureSends(2);
        assertThat(capturedUsers.getAllValues())
                .containsExactlyInAnyOrder(peer1.toString(), peer2.toString());
        assertThat(payloads.getAllValues()).allSatisfy(p -> {
            DmSocketEvent event = (DmSocketEvent) p;
            assertThat(event.type()).isEqualTo("presence");
            assertThat(event.userId()).isEqualTo(SENDER);
            assertThat(event.online()).isFalse();
            assertThat(event.lastSeenAt()).isEqualTo(lastSeen);
            assertThat(event.conversationId()).isNull();
        });
    }
}

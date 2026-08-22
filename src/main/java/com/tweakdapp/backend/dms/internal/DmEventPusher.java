package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.dto.DmSocketEvent;
import com.tweakdapp.backend.dms.internal.events.DmConversationReadEvent;
import com.tweakdapp.backend.dms.internal.events.DmMessageCreatedEvent;
import com.tweakdapp.backend.dms.internal.events.DmMessageDeletedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The single place DM events leave the JVM: everything funnels into the participants'
 * {@code /user/queue/dms} queues (see {@code shared/realtime/WebSocketConfig}).
 *
 * <p>Durable events (message created/deleted, conversation read) arrive as
 * {@link TransactionalEventListener AFTER_COMMIT listeners}, so a rolled-back transaction can never
 * announce a message that does not exist; a user who is offline simply misses the push and catches
 * up over REST. Typing is not an event at all — it is ephemeral and relayed directly by the
 * service.
 */
@Component
class DmEventPusher {

    private static final String QUEUE = "/queue/dms";

    private final SimpMessagingTemplate messagingTemplate;

    DmEventPusher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener
    void on(DmMessageCreatedEvent event) {
        DmSocketEvent socketEvent = DmSocketEvent.messageCreated(event.message());
        push(event.recipientId(), socketEvent);
        push(event.senderId(), socketEvent); // the sender's other devices
    }

    @TransactionalEventListener
    void on(DmMessageDeletedEvent event) {
        DmSocketEvent socketEvent = DmSocketEvent.messageDeleted(event.conversationId(), event.messageId());
        push(event.userA(), socketEvent);
        push(event.userB(), socketEvent);
    }

    @TransactionalEventListener
    void on(DmConversationReadEvent event) {
        DmSocketEvent socketEvent = DmSocketEvent.conversationRead(
                event.conversationId(), event.readerId(), event.lastReadMessageId());
        push(event.peerId(), socketEvent);
        push(event.readerId(), socketEvent); // the reader's other devices
    }

    /** Direct relay (no transaction involved): the peer sees "typing…". */
    void pushTyping(UUID conversationId, UUID typistId, UUID peerId, boolean typing) {
        push(peerId, DmSocketEvent.typing(conversationId, typistId, typing));
    }

    /** One user's presence flipped; tell everyone they share a conversation with (see {@code DmPresencePusher}). */
    void pushPresence(List<UUID> peerIds, UUID userId, boolean online, Instant lastSeenAt) {
        DmSocketEvent socketEvent = DmSocketEvent.presence(userId, online, lastSeenAt);
        for (UUID peerId : peerIds) {
            push(peerId, socketEvent);
        }
    }

    private void push(UUID userId, DmSocketEvent event) {
        messagingTemplate.convertAndSendToUser(userId.toString(), QUEUE, event);
    }
}

package com.tweakdapp.backend.dms.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire format of the {@code /user/queue/dms} WebSocket queue — a sparse envelope discriminated
 * by {@code type}; fields irrelevant to a type are {@code null}.
 *
 * <ul>
 *   <li>{@code message.created} — {@code message} set. Delivered to the recipient, and to the
 *       sender's other devices.</li>
 *   <li>{@code message.deleted} — {@code messageId} set. Delivered to both participants.</li>
 *   <li>{@code conversation.read} — {@code userId} (the reader) and {@code lastReadMessageId} set.
 *       Delivered to the peer (their "read" ticks) and to the reader's other devices (badge
 *       sync).</li>
 *   <li>{@code typing} — {@code userId} (who is typing) and {@code typing} set. Delivered to the
 *       peer only.</li>
 *   <li>{@code presence} — {@code userId}, {@code online} and {@code lastSeenAt} set
 *       ({@code conversationId} is {@code null}). Delivered to users who share a conversation with
 *       the one whose presence flipped.</li>
 * </ul>
 */
public record DmSocketEvent(
        String type,
        UUID conversationId,
        DmMessageDto message,
        UUID messageId,
        UUID userId,
        UUID lastReadMessageId,
        Boolean typing,
        Boolean online,
        Instant lastSeenAt
) {

    public static DmSocketEvent messageCreated(DmMessageDto message) {
        return new DmSocketEvent("message.created", message.conversationId(), message, null, null, null, null,
                null, null);
    }

    public static DmSocketEvent messageDeleted(UUID conversationId, UUID messageId) {
        return new DmSocketEvent("message.deleted", conversationId, null, messageId, null, null, null, null, null);
    }

    public static DmSocketEvent conversationRead(UUID conversationId, UUID readerId, UUID lastReadMessageId) {
        return new DmSocketEvent("conversation.read", conversationId, null, null, readerId, lastReadMessageId, null,
                null, null);
    }

    public static DmSocketEvent typing(UUID conversationId, UUID typistId, boolean typing) {
        return new DmSocketEvent("typing", conversationId, null, null, typistId, null, typing, null, null);
    }

    public static DmSocketEvent presence(UUID userId, boolean online, Instant lastSeenAt) {
        return new DmSocketEvent("presence", null, null, null, userId, null, null, online, lastSeenAt);
    }
}

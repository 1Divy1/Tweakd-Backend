package com.tweakdapp.backend.dms;

import com.tweakdapp.backend.dms.dto.DmConversationPageDto;
import com.tweakdapp.backend.dms.dto.DmMessageDto;
import com.tweakdapp.backend.dms.dto.DmMessagePageDto;
import com.tweakdapp.backend.dms.dto.DmReadReceiptDto;
import com.tweakdapp.backend.dms.dto.SendMessageRequest;

import java.util.UUID;

/**
 * Direct messaging (1:1). All durable operations are REST-driven and pass through here; live
 * delivery (new messages, read receipts, deletions, typing) is pushed to the participants'
 * {@code /user/queue/dms} WebSocket queues after the owning transaction commits.
 */
public interface DmsService {

    /**
     * One keyset page of the caller's chats list, most recently active first. Conversations the
     * caller hid and conversations without any message yet are not listed.
     */
    DmConversationPageDto listConversations(UUID userId, String cursor, int size);

    /**
     * One keyset page of a conversation's messages, newest first, plus the peer's read watermark.
     *
     * @throws com.tweakdapp.backend.dms.exception.DmConversationNotFoundException if the
     *         conversation does not exist — or the caller is not a participant, which is
     *         deliberately the same 404
     */
    DmMessagePageDto listMessages(UUID userId, UUID conversationId, String cursor, int size);

    /**
     * Sends a DM, creating the pair's conversation if this is their first message. Unhides the
     * conversation for both sides and bumps the recipient's unread count. After commit, pushes a
     * {@code message.created} event to the recipient and to the sender's other devices.
     *
     * @throws com.tweakdapp.backend.dms.exception.CannotMessageSelfException if recipient == sender
     * @throws com.tweakdapp.backend.dms.exception.DmRecipientNotFoundException if the recipient
     *         profile does not exist
     */
    DmMessageDto sendMessage(UUID senderId, SendMessageRequest request);

    /**
     * Marks the conversation read up to its latest message: zeroes the caller's unread count and
     * advances their read watermark. After commit, pushes a {@code conversation.read} event to the
     * peer (read ticks) and the caller's other devices (badge sync).
     *
     * @throws com.tweakdapp.backend.dms.exception.DmConversationNotFoundException if the
     *         conversation does not exist or the caller is not a participant
     */
    DmReadReceiptDto markRead(UUID userId, UUID conversationId);

    /**
     * Soft-deletes one of the caller's own messages: the row keeps its place but is blanked and
     * flagged, and both sides render a placeholder. Idempotent. After commit, pushes a
     * {@code message.deleted} event to both participants.
     *
     * @throws com.tweakdapp.backend.dms.exception.DmMessageNotFoundException if the message
     *         does not exist — or the caller is not its sender, which is deliberately the same 404
     */
    void deleteMessage(UUID userId, UUID messageId);

    /**
     * Hides the conversation from the caller's chats list ("delete chat"); the peer's side is
     * untouched, and the next message from either side unhides it. Also zeroes the caller's unread
     * count so the badge cannot count invisible chats.
     *
     * @throws com.tweakdapp.backend.dms.exception.DmConversationNotFoundException if the
     *         conversation does not exist or the caller is not a participant
     */
    void hideConversation(UUID userId, UUID conversationId);

    /** Total unread DMs across all conversations, for the app badge. */
    long countUnread(UUID userId);

    /**
     * Relays a typing indicator to the peer's socket queue. Ephemeral — nothing is persisted; the
     * only check is that the caller actually participates in the conversation (silently dropped
     * otherwise, since there is no requester to answer over the socket).
     */
    void relayTyping(UUID userId, UUID conversationId, boolean typing);
}

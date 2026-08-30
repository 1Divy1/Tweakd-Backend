package com.tweakdapp.backend.dms.dto;

import java.util.UUID;

/**
 * The result of marking a conversation read: the reader's new watermark. {@code lastReadMessageId}
 * is {@code null} when the conversation has no messages yet.
 */
public record DmReadReceiptDto(
        UUID conversationId,
        UUID lastReadMessageId
) {}

package com.carsocialmedia.backend.dms.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One DM. A deleted message keeps its place in the conversation but {@code deleted == true} and
 * {@code content} is empty — the client renders a "message deleted" placeholder for both sides.
 */
public record DmMessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        boolean deleted,
        Instant createdAt
) {}

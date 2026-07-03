package com.carsocialmedia.backend.forums.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for replying to a thread. Everything else (id, author, timestamps, counts) is
 * server-assigned.
 *
 * @param content the reply text (required, non-blank)
 * @param parentPostId the reply being replied to for nested threading, or {@code null} for a
 *                     top-level reply. Must reference a reply in the same thread.
 */
public record CreateReplyRequest(
        @NotBlank @Size(max = 20000) String content,
        UUID parentPostId
) {}

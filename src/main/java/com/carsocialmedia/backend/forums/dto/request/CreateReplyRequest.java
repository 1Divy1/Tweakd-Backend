package com.carsocialmedia.backend.forums.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for replying to a thread. Everything else (id, author, timestamps, counts) is
 * server-assigned.
 *
 * @param content the reply text (required, non-blank)
 * @param parentPostId the reply being replied to for nested threading, or {@code null} for a
 *                     top-level reply. Must reference a reply in the same thread.
 * @param taggedPeople ids of profiles tagged in the reply (optional, deduplicated server-side)
 * @param taggedCars ids of cars tagged in the reply (optional, deduplicated server-side). A car
 *        can only be tagged when its owner is in {@code taggedPeople} — except the reply author's
 *        own cars, which need no self-tag.
 */
public record CreateReplyRequest(
        @NotBlank @Size(max = 20000) String content,
        UUID parentPostId,
        @Size(max = 30) List<@NotNull UUID> taggedPeople,
        @Size(max = 30) List<@NotNull UUID> taggedCars
) {}

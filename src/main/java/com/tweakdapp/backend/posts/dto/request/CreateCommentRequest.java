package com.tweakdapp.backend.posts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for posting a comment on a post. Everything else (id, author, timestamps, like count)
 * is server-assigned.
 *
 * @param content the comment text (required, non-blank)
 * @param parentCommentId the comment being replied to for a threaded reply, or {@code null} for a
 *                        root comment. Must reference a comment on the same post.
 * @param taggedPeople ids of profiles tagged in the comment (optional, deduplicated server-side)
 * @param taggedCars ids of cars tagged in the comment (optional, deduplicated server-side). A car
 *        can only be tagged when its owner is in {@code taggedPeople} — except the comment author's
 *        own cars, which need no self-tag.
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = 2200) String content,
        UUID parentCommentId,
        @Size(max = 30) List<@NotNull UUID> taggedPeople,
        @Size(max = 30) List<@NotNull UUID> taggedCars
) {}
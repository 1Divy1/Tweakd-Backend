package com.carsocialmedia.backend.posts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for posting a comment on a post. Everything else (id, author, timestamps, like count)
 * is server-assigned.
 *
 * @param content the comment text (required, non-blank)
 * @param parentCommentId the comment being replied to for a threaded reply, or {@code null} for a
 *                        root comment. Must reference a comment on the same post.
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = 2200) String content,
        UUID parentCommentId
) {}
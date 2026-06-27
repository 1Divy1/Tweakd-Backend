package com.carsocialmedia.backend.posts.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for creating a post. Carries only what the author chooses on the compose screen;
 * everything else (id, author, counts, timestamps) is server-assigned.
 *
 * Images are <em>not</em> part of this payload. The flow mirrors the garage "add car" wizard:
 * the post row is created first (so its id exists), then Flutter requests a presigned upload URL
 * per image, uploads to R2, and finally sends the resulting keys back via
 * {@code PATCH /api/v1/posts/{postId}/images}.
 *
 * The four {@code *CountEnabled} flags are the author's per-post visibility toggles for the
 * like / comment / share / saved counts. They are optional and default to {@code true} when omitted.
 *
 * @param description the post caption (may be empty; null is treated as empty)
 * @param taggedPeople ids of profiles tagged in the post (optional, deduplicated server-side)
 * @param taggedCars ids of cars tagged in the post (optional, deduplicated server-side)
 * @param likesCountEnabled whether to expose the like count (default true)
 * @param commentsCountEnabled whether to expose the comment count (default true)
 * @param sharesCountEnabled whether to expose the share count (default true)
 * @param savedCountEnabled whether to expose the saved count (default true)
 */
public record CreatePostRequest(
        @Size(max = 2200) String description,
        @Size(max = 30) List<@NotNull UUID> taggedPeople,
        @Size(max = 30) List<@NotNull UUID> taggedCars,
        Boolean likesCountEnabled,
        Boolean commentsCountEnabled,
        Boolean sharesCountEnabled,
        Boolean savedCountEnabled
) {}
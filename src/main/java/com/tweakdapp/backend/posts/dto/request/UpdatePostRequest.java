package com.tweakdapp.backend.posts.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Partial-update payload for an existing post. PATCH semantics: a {@code null} field means
 * "leave unchanged".
 *
 * <ul>
 *   <li>{@code description} — null leaves it; a value (including {@code ""}) replaces the caption.</li>
 *   <li>{@code taggedPeople} / {@code taggedCars} — null leaves them; a non-null list (including an
 *       empty list, which clears all tags) replaces the whole set.</li>
 *   <li>{@code *CountEnabled} — null leaves the toggle; a value sets it.</li>
 * </ul>
 *
 * Images are not edited here — they have their own {@code PATCH /api/v1/posts/{postId}/images}
 * endpoint.
 *
 * @param description the new caption, or null to leave unchanged
 * @param taggedPeople the complete new set of tagged profile ids, or null to leave unchanged
 * @param taggedCars the complete new set of tagged car ids, or null to leave unchanged
 * @param likesCountEnabled new like-count visibility, or null to leave unchanged
 * @param commentsCountEnabled new comment-count visibility, or null to leave unchanged
 * @param sharesCountEnabled new share-count visibility, or null to leave unchanged
 * @param savedCountEnabled new saved-count visibility, or null to leave unchanged
 */
public record UpdatePostRequest(
        @Size(max = 2200) String description,
        @Size(max = 30) List<@NotNull UUID> taggedPeople,
        @Size(max = 30) List<@NotNull UUID> taggedCars,
        Boolean likesCountEnabled,
        Boolean commentsCountEnabled,
        Boolean sharesCountEnabled,
        Boolean savedCountEnabled
) {}
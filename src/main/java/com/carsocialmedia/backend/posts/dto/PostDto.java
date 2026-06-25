package com.carsocialmedia.backend.posts.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A post as returned in a feed or detail view.
 *
 * Bundles everything needed to render a post card in one response: the author, ordered
 * images, tagged people and cars, denormalized engagement counts, and per-viewer state
 * (whether the authenticated user liked / saved it). Unbounded collections — comments and
 * the list of users who liked — are intentionally <em>not</em> embedded; they are fetched
 * via their own paginated endpoints.
 *
 * Tagged people reuse the profile module's {@link ProfileSearchResultDto} and tagged cars
 * reuse the garage module's {@link CarSummaryDto}; both are resolved by the service through
 * those modules' public interfaces (no cross-module table access).
 *
 * @param id the post ID
 * @param description the post text
 * @param author the user who created the post (id, username, avatarUrl)
 * @param images the post's images, ordered by display order
 * @param taggedPeople profiles tagged in the post
 * @param taggedCars cars tagged in the post
 * @param likesCount denormalized like count
 * @param commentsCount denormalized comment count
 * @param sharesCount denormalized share count
 * @param likesCountEnabled whether the author exposes the like count
 * @param commentsCountEnabled whether the author exposes the comment count
 * @param sharesCountEnabled whether the author exposes the share count
 * @param viewerHasLiked whether the requesting user has liked this post
 * @param viewerHasSaved whether the requesting user has saved this post
 * @param createdAt when the post was created
 * @param updatedAt when the post was last updated
 */
public record PostDto(
        UUID id,
        String description,
        ProfileSearchResultDto author,
        List<PostImageDto> images,
        List<ProfileSearchResultDto> taggedPeople,
        List<CarSummaryDto> taggedCars,

        long likesCount,
        long commentsCount,
        long sharesCount,
        boolean likesCountEnabled,
        boolean commentsCountEnabled,
        boolean sharesCountEnabled,

        boolean viewerHasLiked,
        boolean viewerHasSaved,

        Instant createdAt,
        Instant updatedAt
) {}
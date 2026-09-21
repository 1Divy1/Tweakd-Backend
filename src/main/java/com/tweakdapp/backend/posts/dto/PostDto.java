package com.tweakdapp.backend.posts.dto;

import com.tweakdapp.backend.mapevents.dto.ParticipantCardDto;
import com.tweakdapp.backend.garage.dto.ModShareCardDto;

import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

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
 * @param sharesCount how many users reposted the post
 * @param savedCount denormalized count of how many users saved the post
 * @param likesCountEnabled whether the author exposes the like count
 * @param commentsCountEnabled whether the author exposes the comment count
 * @param sharesCountEnabled whether the author exposes the repost count
 * @param savedCountEnabled whether the author exposes the saved count
 * @param viewerHasLiked whether the requesting user has liked this post
 * @param viewerHasSaved whether the requesting user has saved this post
 * @param createdAt when the post was created
 * @param updatedAt when the post was last updated
 * @param participantCard the participant card this post shares, drawn in place of images —
 *        re-derived on every read, so it always shows the real placements; {@code null} on an
 *        ordinary post, and on one whose card no longer derives (car gone, entry revoked)
 * @param viewerHasReposted whether the requesting user has reposted this post
 * @param modShareCard the build-log modification this post shares, drawn in place of images —
 *        re-derived on every read, so an edited mod updates everywhere it was shared; {@code null}
 *        on an ordinary post, and on one whose mod has since been deleted
 * @param repostedBy which accounts the viewer follows reposted it — filled on the global feed only,
 *        where it explains why the post is there; {@code null} everywhere else and when none did
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
        long savedCount,
        boolean likesCountEnabled,
        boolean commentsCountEnabled,
        boolean sharesCountEnabled,
        boolean savedCountEnabled,

        boolean viewerHasLiked,
        boolean viewerHasSaved,

        Instant createdAt,
        Instant updatedAt,
        ParticipantCardDto participantCard,
        ModShareCardDto modShareCard,
        boolean viewerHasReposted,
        RepostedByDto repostedBy
) {}
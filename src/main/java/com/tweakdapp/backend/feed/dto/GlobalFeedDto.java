package com.tweakdapp.backend.feed.dto;

import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.posts.dto.PostDto;

import java.util.List;

/**
 * One keyset page of the global feed, plus the badge celebrations the app owes the caller.
 *
 * <p>{@code items} and {@code nextCursor} are exactly the {@code posts} module's
 * {@link com.tweakdapp.backend.posts.dto.PostPageDto} — same field names, same positions — so the
 * app's existing feed parsing is unaffected by the extra field.
 *
 * @param items    the posts in this page, highest ranked first
 * @param nextCursor opaque token for the next page, or {@code null} on the last page
 * @param pendingBadgeCelebrations the caller's earned-but-not-yet-animated badges, oldest unlock
 *        first. <strong>First page only</strong> ({@code cursor == null}): the app fetches page one
 *        on launch, which is exactly when it plays the Duolingo-style unlock animation, so there is
 *        no separate request for it. Empty (never null) on every paged request, so scrolling the
 *        feed never re-triggers an animation. The app acknowledges each one it plays through
 *        {@code POST /api/v1/badges/me/pending-celebration/{badgeId}}.
 */
public record GlobalFeedDto(
        List<PostDto> items,
        String nextCursor,
        List<UserBadgeDto> pendingBadgeCelebrations
) {}

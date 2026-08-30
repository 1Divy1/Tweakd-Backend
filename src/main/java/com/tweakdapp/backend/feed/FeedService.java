package com.tweakdapp.backend.feed;

import com.tweakdapp.backend.posts.dto.PostPageDto;

/**
 * Assembles the feeds shown on the app's feed page.
 *
 * <p>The app has two feeds: a <em>global</em> feed of the most viral posts across the whole app,
 * and (planned) a <em>personalized</em> feed of posts from accounts the user follows, tuned by the
 * user's preferences. Only the global feed is implemented for now.
 *
 * <p>This module owns feed <em>policy</em> (which posts, in what order, for which feed) and composes
 * the {@code posts} module for the underlying post data and assembly.
 */
public interface FeedService {

    /**
     * One keyset page of the global feed: the most viral posts across the app, highest ranked
     * first. Ordering is driven by the Supabase-maintained {@code ranking_score}.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject (for per-post like / save flags)
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum by the posts module)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     */
    PostPageDto getGlobalFeed(String currentUserId, String cursor, int size);
}

package com.tweakdapp.backend.posts.dto;

import java.util.List;

/**
 * One page of posts (keyset pagination), newest first.
 *
 * Used for a profile's post grid — the caller's own posts and, privacy permitting, another
 * user's. The client echoes {@code nextCursor} back as {@code ?cursor=} to fetch the next page.
 *
 * @param items the posts in this page, fully assembled
 * @param nextCursor opaque token for the next page, or {@code null} if this is the last page
 */
public record PostPageDto(
        List<PostDto> items,
        String nextCursor
) {}
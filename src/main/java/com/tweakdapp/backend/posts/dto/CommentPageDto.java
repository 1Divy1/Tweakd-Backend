package com.tweakdapp.backend.posts.dto;

import java.util.List;

/**
 * One page of a post's comments (keyset pagination).
 *
 * @param items the comments in this page, newest first
 * @param nextCursor opaque token to fetch the next page, or {@code null} if this is the last page
 */
public record CommentPageDto(
        List<CommentDto> items,
        String nextCursor
) {}
package com.carsocialmedia.backend.posts;

import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;

import java.util.UUID;

public interface PostsService {

    /**
     * One keyset page of a post's root comments, newest first.
     *
     * @param viewerId the requesting user (to populate per-comment {@code viewerHasLiked})
     * @param postId the post whose comments to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max number of comments to return (clamped to a sane maximum)
     * @return the page of comments plus the cursor for the next page (or {@code null} if last)
     */
    CommentPageDto getComments(UUID viewerId, UUID postId, String cursor, int size);

    /**
     * One keyset page of the users who liked a post, most recent liker first.
     *
     * @param postId the post whose likers to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max number of likers to return (clamped to a sane maximum)
     * @return the page of likers plus the cursor for the next page (or {@code null} if last)
     */
    LikerPageDto getPostLikers(UUID postId, String cursor, int size);
}
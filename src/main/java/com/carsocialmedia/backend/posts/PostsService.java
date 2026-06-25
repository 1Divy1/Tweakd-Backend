package com.carsocialmedia.backend.posts;

import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;
import com.carsocialmedia.backend.posts.dto.PostDto;
import com.carsocialmedia.backend.posts.dto.PostPageDto;
import com.carsocialmedia.backend.posts.dto.request.CreatePostRequest;
import com.carsocialmedia.backend.posts.dto.request.UpdatePostRequest;

import java.util.List;
import java.util.UUID;

public interface PostsService {

    /**
     * Creates a post owned by the current user, with its tagged people and tagged cars.
     *
     * Images are added in a separate step: the returned post has no images yet. Flutter requests
     * a presigned upload URL per image from the storage module, uploads to R2, then calls
     * {@link #savePostImageKeys} with the resulting keys.
     *
     * @param currentUserId the author's UUID from the JWT subject
     * @param request the caption, tagged people / cars, and count-visibility toggles
     * @return the created post (no images, zeroed counts, viewer flags false)
     * @throws com.carsocialmedia.backend.posts.exception.InvalidReferenceException if a tagged
     *         profile or car does not exist
     */
    PostDto createPost(String currentUserId, CreatePostRequest request);

    /**
     * Returns a single post by id, fully assembled (author, images, tagged people / cars, counts
     * and the viewer's like / save state). This is the building block used both for a post's
     * detail view and, later, for rendering a feed or a profile's post grid.
     *
     * Visibility mirrors the profile / garage privacy gate: anyone may read a post by a public
     * author, but a private author's posts are visible only to the author or an accepted follower.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject
     * @param postId the post to fetch
     * @return the assembled post
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.PrivatePostException if the author is private
     *         and the viewer is not an accepted follower
     */
    PostDto getPost(String currentUserId, UUID postId);

    /**
     * Partially updates a post owned by the current user. Only non-null fields of the request are
     * applied; a non-null {@code taggedPeople} / {@code taggedCars} list replaces the whole set
     * (empty clears it). Images are not edited here — see {@link #savePostImageKeys}.
     *
     * @param currentUserId the requesting user's UUID from the JWT subject
     * @param postId the post to update
     * @param request the partial update
     * @return the updated post, fully assembled
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.NotPostOwnerException if the user is not the author
     * @throws com.carsocialmedia.backend.posts.exception.InvalidReferenceException if a newly tagged
     *         profile or car does not exist
     */
    PostDto updatePost(String currentUserId, UUID postId, UpdatePostRequest request);

    /**
     * Deletes a post owned by the current user. Child rows (images, tags, likes, shares, saves,
     * comments, reports) are removed by the Supabase {@code ON DELETE CASCADE}; the post's R2
     * image objects are deleted after the transaction commits.
     *
     * @param currentUserId the requesting user's UUID from the JWT subject
     * @param postId the post to delete
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.NotPostOwnerException if the user is not the author
     */
    void deletePost(String currentUserId, UUID postId);

    /**
     * One keyset page of the current user's own posts, newest first. No privacy gating — a user
     * always sees their own posts.
     *
     * @param currentUserId the requesting user's UUID from the JWT subject
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     */
    PostPageDto getMyPosts(String currentUserId, String cursor, int size);

    /**
     * One keyset page of another user's posts, newest first. Subject to the same privacy gate as
     * {@link #getPost}: a private author's posts are visible only to accepted followers.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject
     * @param username the author whose posts to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     * @throws com.carsocialmedia.backend.profile.exception.ProfileNotFoundException if the username
     *         does not resolve to a profile
     * @throws com.carsocialmedia.backend.posts.exception.PrivatePostException if the author is private
     *         and the viewer is not an accepted follower
     */
    PostPageDto getUserPosts(String currentUserId, String username, String cursor, int size);

    /**
     * Replaces a post's images with the given R2 keys, in order (position = list index). Keys no
     * longer present are deleted from R2 after the transaction commits. An empty list clears the
     * post's images.
     *
     * @param currentUserId the requesting user's UUID from the JWT subject
     * @param postId the post to attach images to
     * @param imageKeys the R2 object keys, in display order
     * @return the updated post with its images resolved to full URLs
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.NotPostOwnerException if the user is not the author
     */
    PostDto savePostImageKeys(String currentUserId, UUID postId, List<String> imageKeys);

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
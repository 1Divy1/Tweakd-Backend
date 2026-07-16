package com.carsocialmedia.backend.posts;

import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;
import com.carsocialmedia.backend.posts.dto.PostDto;
import com.carsocialmedia.backend.posts.dto.PostPageDto;
import com.carsocialmedia.backend.posts.dto.CommentDto;
import com.carsocialmedia.backend.posts.dto.request.CreateCommentRequest;
import com.carsocialmedia.backend.posts.dto.request.CreatePostRequest;
import com.carsocialmedia.backend.posts.dto.request.UpdatePostRequest;

import java.util.List;
import java.util.Optional;
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
     * All accounts are public, so any post is readable by any authenticated user.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject
     * @param postId the post to fetch
     * @return the assembled post
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
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
     * One keyset page of another user's posts, newest first. All accounts are public, so any user's
     * posts are visible to any authenticated viewer.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject
     * @param username the author whose posts to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     * @throws com.carsocialmedia.backend.profile.exception.ProfileNotFoundException if the username
     *         does not resolve to a profile
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
     * One keyset page of the global feed: every post ranked by virality (the Supabase-maintained
     * {@code ranking_score}), most viral first. This is the data behind the app's global feed; the
     * {@code feed} module wraps it. There is no privacy gate — all accounts are public.
     *
     * <p>Because the ranking score is mutable, the feed is only eventually consistent across pages:
     * a post may occasionally repeat or be skipped as scores shift between requests.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject (for per-post like / save flags)
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     */
    PostPageDto getRankedPosts(String currentUserId, String cursor, int size);

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
     * One keyset page of the replies to a single comment (its direct children), newest first.
     *
     * @param viewerId the requesting user (to populate per-comment {@code viewerHasLiked})
     * @param postId the post the parent comment belongs to (from the URL)
     * @param commentId the parent comment whose replies to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max number of replies to return (clamped to a sane maximum)
     * @return the page of replies plus the cursor for the next page (or {@code null} if last)
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if the parent comment
     *         does not exist or does not belong to the post
     */
    CommentPageDto getReplies(UUID viewerId, UUID postId, UUID commentId, String cursor, int size);

    /**
     * One keyset page of the current user's saved (bookmarked) posts, newest save first.
     *
     * @param currentUserId the requesting user's UUID from the JWT subject
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     */
    PostPageDto getSavedPosts(String currentUserId, String cursor, int size);

    /**
     * One keyset page of the current user's shared posts, newest share first.
     *
     * @param currentUserId the requesting user's UUID from the JWT subject
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max posts to return (clamped to a sane maximum)
     * @return the page of posts plus the cursor for the next page (or {@code null} if last)
     */
    PostPageDto getSharedPosts(String currentUserId, String cursor, int size);

    /**
     * One keyset page of the users who liked a post, most recent liker first.
     *
     * @param postId the post whose likers to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max number of likers to return (clamped to a sane maximum)
     * @return the page of likers plus the cursor for the next page (or {@code null} if last)
     */
    LikerPageDto getPostLikers(UUID postId, String cursor, int size);

    // -------------------------------------------------------------------
    // ENGAGEMENT — likes, saves, shares, comments
    //
    // The denormalized counts (posts.likes_count / saved_count / shares_count /
    // quote_shares_count / comments_count and comments.likes_count) are all maintained by
    // Supabase triggers on the engagement tables, so these methods only insert/delete the
    // engagement rows and never touch a count column. The "add" operations are idempotent: a
    // duplicate is a no-op, not an error.
    // -------------------------------------------------------------------

    /**
     * Likes a post on behalf of the current user. Idempotent — liking an already-liked post is a
     * no-op. The {@code likes_count} is updated by a Supabase trigger.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post to like
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     */
    void likePost(String currentUserId, UUID postId);

    /**
     * Removes the current user's like from a post. Idempotent — unliking a post the user has not
     * liked is a no-op.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post to unlike
     */
    void unlikePost(String currentUserId, UUID postId);

    /**
     * Saves (bookmarks) a post for the current user. Idempotent — saving an already-saved post is a
     * no-op. The {@code saved_count} is updated by a Supabase trigger.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post to save
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     */
    void savePost(String currentUserId, UUID postId);

    /**
     * Removes the current user's save of a post. Idempotent — unsaving a post the user has not
     * saved is a no-op.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post to unsave
     */
    void unsavePost(String currentUserId, UUID postId);

    /**
     * Shares a post on behalf of the current user, optionally with the sharer's own caption. A
     * non-blank caption makes it a "quote share" (counted in {@code quote_shares_count}); a
     * null/blank caption is a plain share. Idempotent — sharing a post the user has already shared
     * is a no-op (the existing share, including its caption, is left untouched). The share counts
     * are updated by a Supabase trigger.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post to share
     * @param content the sharer's caption, or null/blank for a plain share
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     */
    void sharePost(String currentUserId, UUID postId, String content);

    /**
     * Removes the current user's share of a post. Idempotent — unsharing a post the user has not
     * shared is a no-op.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post to unshare
     */
    void unsharePost(String currentUserId, UUID postId);

    /**
     * Adds a comment (or threaded reply) to a post on behalf of the current user. The
     * {@code comments_count} is updated by a Supabase trigger.
     *
     * @param currentUserId the author's UUID from the JWT subject
     * @param postId the post being commented on
     * @param request the comment text and optional {@code parentCommentId} for a reply
     * @return the created comment, fully assembled (author resolved, zero likes, not liked by viewer)
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if a
     *         {@code parentCommentId} is given but does not reference a comment on this post
     */
    CommentDto addComment(String currentUserId, UUID postId, CreateCommentRequest request);

    /**
     * Soft-deletes a comment: the row is kept (it may still anchor replies) but flagged deleted and
     * rendered as "[deleted]". Allowed for the comment's author or the post's owner (post-owner
     * moderation). Idempotent — deleting an already-deleted comment is a no-op. The
     * {@code comments_count} is decremented by a Supabase trigger when the comment flips to deleted.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post the comment belongs to (from the URL)
     * @param commentId the comment to delete
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if the comment does
     *         not exist or does not belong to the post
     * @throws com.carsocialmedia.backend.posts.exception.NotCommentOwnerException if the user is neither
     *         the comment author nor the post owner
     */
    void deleteComment(String currentUserId, UUID postId, UUID commentId);

    /**
     * Likes a comment on behalf of the current user. Idempotent — liking an already-liked comment
     * is a no-op. The comment's {@code likes_count} is updated by a Supabase trigger.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post the comment belongs to (from the URL)
     * @param commentId the comment to like
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if the comment does
     *         not exist or does not belong to the post
     */
    void likeComment(String currentUserId, UUID postId, UUID commentId);

    /**
     * Removes the current user's like from a comment. Idempotent — unliking a comment the user has
     * not liked is a no-op.
     *
     * @param currentUserId the acting user's UUID from the JWT subject
     * @param postId the post the comment belongs to (from the URL)
     * @param commentId the comment to unlike
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if the comment does
     *         not exist or does not belong to the post
     */
    void unlikeComment(String currentUserId, UUID postId, UUID commentId);

    // -------------------------------------------------------------------
    // REPORTING
    //
    // The report rows themselves live in the report module; these methods perform the
    // posts-side checks (the target exists, and the reporter is not reporting their own content)
    // and then delegate the insert to report.ReportService. Listing the preset report reasons is a
    // pure passthrough to the report module and is exposed directly by the controller.
    // -------------------------------------------------------------------

    /**
     * Files a report against a post on behalf of the current user.
     *
     * @param currentUserId the reporting user's UUID from the JWT subject
     * @param postId the post being reported
     * @param reasonId an optional preset reason (a {@code post}-scoped {@code report_reasons} id), or {@code null}
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     * @throws com.carsocialmedia.backend.posts.exception.CannotReportOwnContentException if the caller owns the post
     * @throws com.carsocialmedia.backend.report.exception.InvalidReportReasonException if {@code reasonId}
     *         is given but is not a valid {@code post} reason
     * @throws com.carsocialmedia.backend.report.exception.DuplicateReportException if the caller already
     *         reported this post
     */
    void reportPost(String currentUserId, UUID postId, UUID reasonId);

    // -------------------------------------------------------------------
    // MODERATION — called by the admin module; no auth logic here
    // -------------------------------------------------------------------

    /**
     * The reported post, in the uniform moderation shape (author, caption, resolved image URLs), or
     * empty if the post is gone. Empty rather than an exception on purpose: the caller runs inside
     * its own transaction, and a not-found thrown across that boundary would mark it rollback-only.
     */
    Optional<com.carsocialmedia.backend.shared.moderation.ModerationContentDto> findPostModerationSnapshot(UUID postId);

    /**
     * The reported comment, in the uniform moderation shape, or empty if the comment is gone (see
     * {@link #findPostModerationSnapshot}). Unlike the user-facing comment endpoints there is no
     * post id here — a report row only carries the comment id.
     */
    Optional<com.carsocialmedia.backend.shared.moderation.ModerationContentDto> findCommentModerationSnapshot(UUID commentId);

    /**
     * Hard-deletes a post on a moderator's behalf, bypassing the ownership check but otherwise
     * identical to {@link #deletePost} (DB cascade + R2 image cleanup after commit). The admin
     * module is responsible for snapshotting the content into the audit log <em>before</em> calling
     * this — the report rows cascade away with the post.
     *
     * @throws com.carsocialmedia.backend.posts.exception.PostNotFoundException if the post does not exist
     */
    void deletePostAsModerator(UUID postId);

    /**
     * Deletes a comment on a moderator's behalf, bypassing the ownership check but otherwise
     * identical to {@link #deleteComment} (soft delete — the row may still anchor replies).
     * Idempotent.
     *
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if the comment does not exist
     */
    void deleteCommentAsModerator(UUID commentId);

    /**
     * Files a report against a comment on behalf of the current user.
     *
     * @param currentUserId the reporting user's UUID from the JWT subject
     * @param postId the post the comment belongs to (from the URL)
     * @param commentId the comment being reported
     * @param reasonId an optional preset reason (a {@code comment}-scoped {@code report_reasons} id), or {@code null}
     * @throws com.carsocialmedia.backend.posts.exception.CommentNotFoundException if the comment does not
     *         exist or does not belong to the post
     * @throws com.carsocialmedia.backend.posts.exception.CannotReportOwnContentException if the caller authored the comment
     * @throws com.carsocialmedia.backend.report.exception.InvalidReportReasonException if {@code reasonId}
     *         is given but is not a valid {@code comment} reason
     * @throws com.carsocialmedia.backend.report.exception.DuplicateReportException if the caller already
     *         reported this comment
     */
    void reportComment(String currentUserId, UUID postId, UUID commentId, UUID reasonId);
}
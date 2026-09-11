package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.posts.dto.request.ShareParticipantCardRequest;

import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.dto.CommentPageDto;
import com.tweakdapp.backend.posts.dto.LikerPageDto;
import com.tweakdapp.backend.posts.dto.PostDto;
import com.tweakdapp.backend.posts.dto.PostPageDto;
import com.tweakdapp.backend.posts.dto.CommentDto;
import com.tweakdapp.backend.posts.dto.request.CreateCommentRequest;
import com.tweakdapp.backend.posts.dto.request.CreatePostRequest;
import com.tweakdapp.backend.posts.dto.request.PostImageKeysRequest;
import com.tweakdapp.backend.posts.dto.request.SharePostRequest;
import com.tweakdapp.backend.posts.dto.request.UpdatePostRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostsService postsService;

    public PostController(PostsService postsService) {
        this.postsService = postsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto createPost(@AuthenticationPrincipal Jwt jwt,
                             @Valid @RequestBody CreatePostRequest request) {
        return postsService.createPost(jwt.getSubject(), request);
    }

    /**
     * Shares one of the caller's participant cards to the feed. 404 if it is not a card of theirs;
     * 409 {@code participant_card_cooldown} (with {@code details.next_post_allowed_at}) inside the
     * repost cooldown.
     */
    @PostMapping("/participant-card")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto shareParticipantCard(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ShareParticipantCardRequest request) {
        return postsService.shareParticipantCard(jwt.getSubject(), request);
    }

    /**
     * Replaces the post's images with the given R2 keys, in order. Called after Flutter has
     * uploaded each image to R2 using the presigned URLs from the storage module.
     */
    @PatchMapping("/{postId}/images")
    public PostDto updatePostImages(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID postId,
                                    @Valid @RequestBody PostImageKeysRequest request) {
        return postsService.savePostImageKeys(jwt.getSubject(), postId, request.keys());
    }

    /**
     * One keyset page of the current user's own posts, newest first. The client passes the
     * {@code nextCursor} from the previous response back as {@code ?cursor=} to page on.
     */
    @GetMapping("/me")
    public PostPageDto getMyPosts(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") int size) {
        return postsService.getMyPosts(jwt.getSubject(), cursor, size);
    }

    /**
     * One keyset page of another user's posts, newest first. Privacy-gated: a private author's
     * posts are visible only to accepted followers.
     */
    @GetMapping("/by-username/{username}")
    public PostPageDto getUserPosts(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable String username,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "20") int size) {
        return postsService.getUserPosts(jwt.getSubject(), username, cursor, size);
    }

    /**
     * One keyset page of the caller's saved (bookmarked) posts, newest save first. The client
     * passes the {@code nextCursor} from the previous response back as {@code ?cursor=} to page on.
     */
    @GetMapping("/saved")
    public PostPageDto getSavedPosts(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int size) {
        return postsService.getSavedPosts(jwt.getSubject(), cursor, size);
    }

    /**
     * One keyset page of the caller's shared posts, newest share first. The client passes the
     * {@code nextCursor} from the previous response back as {@code ?cursor=} to page on.
     */
    @GetMapping("/shared")
    public PostPageDto getSharedPosts(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "20") int size) {
        return postsService.getSharedPosts(jwt.getSubject(), cursor, size);
    }

    @GetMapping("/{postId}")
    public PostDto readPost(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID postId) {
        return postsService.getPost(jwt.getSubject(), postId);
    }

    @PatchMapping("/{postId}")
    public PostDto updatePost(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID postId,
                              @Valid @RequestBody UpdatePostRequest request) {
        return postsService.updatePost(jwt.getSubject(), postId, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId) {
        postsService.deletePost(jwt.getSubject(), postId);
    }

    /**
     * One keyset page of a post's root comments, newest first. The client passes the
     * {@code nextCursor} from the previous response back as {@code ?cursor=} to page on.
     */
    @GetMapping("/{postId}/comments")
    public CommentPageDto getComments(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID postId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "20") int size) {
        return postsService.getComments(UUID.fromString(jwt.getSubject()), postId, cursor, size);
    }

    /**
     * One keyset page of the replies to a single comment (its direct children), newest first. The
     * client passes the {@code nextCursor} from the previous response back as {@code ?cursor=}.
     */
    @GetMapping("/{postId}/comments/{commentId}/replies")
    public CommentPageDto getReplies(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID postId,
                                     @PathVariable UUID commentId,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int size) {
        return postsService.getReplies(UUID.fromString(jwt.getSubject()), postId, commentId, cursor, size);
    }

    /**
     * One keyset page of the users who liked a post, most recent liker first.
     */
    @GetMapping("/{postId}/likes")
    public LikerPageDto getPostLikers(@PathVariable UUID postId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "20") int size) {
        return postsService.getPostLikers(postId, cursor, size);
    }

    // -------------------------------------------------------------------
    // ENGAGEMENT — likes, saves, shares, comments
    // -------------------------------------------------------------------

    /** Likes a post (idempotent). */
    @PostMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likePost(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID postId) {
        postsService.likePost(jwt.getSubject(), postId);
    }

    /** Removes the caller's like from a post (idempotent). */
    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId) {
        postsService.unlikePost(jwt.getSubject(), postId);
    }

    /** Saves (bookmarks) a post for the caller (idempotent). */
    @PostMapping("/{postId}/saves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void savePost(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID postId) {
        postsService.savePost(jwt.getSubject(), postId);
    }

    /** Removes the caller's save of a post (idempotent). */
    @DeleteMapping("/{postId}/saves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsavePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId) {
        postsService.unsavePost(jwt.getSubject(), postId);
    }

    /**
     * Shares a post (idempotent). The optional body's {@code content} is the sharer's caption; a
     * non-blank value makes it a quote share. The body may be omitted for a plain share.
     */
    @PostMapping("/{postId}/shares")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sharePost(@AuthenticationPrincipal Jwt jwt,
                          @PathVariable UUID postId,
                          @Valid @RequestBody(required = false) SharePostRequest request) {
        postsService.sharePost(jwt.getSubject(), postId, request == null ? null : request.content());
    }

    /** Removes the caller's share of a post (idempotent). */
    @DeleteMapping("/{postId}/shares")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsharePost(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID postId) {
        postsService.unsharePost(jwt.getSubject(), postId);
    }

    /** Adds a comment (or threaded reply) to a post. */
    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID postId,
                                 @Valid @RequestBody CreateCommentRequest request) {
        return postsService.addComment(jwt.getSubject(), postId, request);
    }

    /** Soft-deletes a comment (idempotent); allowed for the comment's author or the post's owner. */
    @DeleteMapping("/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID postId,
                              @PathVariable UUID commentId) {
        postsService.deleteComment(jwt.getSubject(), postId, commentId);
    }

    /** Likes a comment (idempotent). */
    @PostMapping("/{postId}/comments/{commentId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeComment(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID postId,
                            @PathVariable UUID commentId) {
        postsService.likeComment(jwt.getSubject(), postId, commentId);
    }

    /** Removes the caller's like from a comment (idempotent). */
    @DeleteMapping("/{postId}/comments/{commentId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeComment(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID postId,
                              @PathVariable UUID commentId) {
        postsService.unlikeComment(jwt.getSubject(), postId, commentId);
    }
}
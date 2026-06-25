package com.carsocialmedia.backend.posts.internal;

import com.carsocialmedia.backend.posts.PostsService;
import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;
import com.carsocialmedia.backend.posts.dto.PostDto;
import com.carsocialmedia.backend.posts.dto.PostPageDto;
import com.carsocialmedia.backend.posts.dto.request.CreatePostRequest;
import com.carsocialmedia.backend.posts.dto.request.PostImageKeysRequest;
import com.carsocialmedia.backend.posts.dto.request.UpdatePostRequest;
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
     * One keyset page of the users who liked a post, most recent liker first.
     */
    @GetMapping("/{postId}/likes")
    public LikerPageDto getPostLikers(@PathVariable UUID postId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "20") int size) {
        return postsService.getPostLikers(postId, cursor, size);
    }
}
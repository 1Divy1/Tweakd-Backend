package com.carsocialmedia.backend.posts.internal;

import com.carsocialmedia.backend.posts.PostsService;
import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;
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
    public void createPost() {
    }

    @GetMapping
    public void readPost() {

    }

    @PatchMapping
    public void updatePost() {

    }

    @DeleteMapping
    public void deletePost() {

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
package com.carsocialmedia.backend.forums.internal;

import com.carsocialmedia.backend.forums.ForumsService;
import com.carsocialmedia.backend.forums.dto.CursorPage;
import com.carsocialmedia.backend.forums.dto.ReplyDto;
import com.carsocialmedia.backend.forums.dto.ThreadCardDto;
import com.carsocialmedia.backend.forums.dto.ThreadDetailDto;
import com.carsocialmedia.backend.forums.dto.TopicGroupDto;
import com.carsocialmedia.backend.forums.dto.request.CreateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateThreadRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateThreadRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Forum reads (topics, feed, brand/model/topic hubs, thread detail, replies) and content writes
 * (create thread, reply, like/unlike, delete). Thin: pulls the JWT subject and delegates to
 * {@link ForumsService}. Shortcuts live in {@link ForumShortcutController}.
 */
@RestController
@RequestMapping("/api/v1/forums")
public class ForumController {

    private final ForumsService forumsService;

    public ForumController(ForumsService forumsService) {
        this.forumsService = forumsService;
    }

    // ---- topics ------------------------------------------------------------

    /** The active topics, grouped by kind ({@code component} / {@code format}). */
    @GetMapping("/topics")
    public List<TopicGroupDto> getTopics() {
        return forumsService.listTopics();
    }

    // ---- feed & hubs -------------------------------------------------------

    /** The global thread feed. {@code sort} = {@code hot} (default) | {@code new} | {@code active}. */
    @GetMapping("/feed")
    public CursorPage<ThreadCardDto> getFeed(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "hot") String sort,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "20") int size) {
        return forumsService.getFeed(jwt.getSubject(), sort, cursor, size);
    }

    /** Threads scoped to a brand. */
    @GetMapping("/brands/{brandId}/threads")
    public CursorPage<ThreadCardDto> getBrandThreads(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID brandId,
                                                     @RequestParam(defaultValue = "hot") String sort,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return forumsService.getBrandThreads(jwt.getSubject(), brandId, sort, cursor, size);
    }

    /** Threads scoped to a model, optionally refined by {@code topic}. */
    @GetMapping("/models/{modelId}/threads")
    public CursorPage<ThreadCardDto> getModelThreads(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID modelId,
                                                     @RequestParam(defaultValue = "hot") String sort,
                                                     @RequestParam(required = false) String topic,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return forumsService.getModelThreads(jwt.getSubject(), modelId, sort, topic, cursor, size);
    }

    /** Threads scoped to a topic, optionally refined by {@code brand} and/or {@code model}. */
    @GetMapping("/topics/{topicId}/threads")
    public CursorPage<ThreadCardDto> getTopicThreads(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable String topicId,
                                                     @RequestParam(defaultValue = "hot") String sort,
                                                     @RequestParam(required = false) UUID brand,
                                                     @RequestParam(required = false) UUID model,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return forumsService.getTopicThreads(jwt.getSubject(), topicId, sort, brand, model, cursor, size);
    }

    // ---- thread detail & replies -------------------------------------------

    @GetMapping("/threads/{threadId}")
    public ThreadDetailDto getThread(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID threadId) {
        return forumsService.getThread(jwt.getSubject(), threadId);
    }

    /** One keyset page of a thread's top-level replies (no children — expand on demand). */
    @GetMapping("/threads/{threadId}/replies")
    public CursorPage<ReplyDto> getReplies(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID threadId,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "20") int size) {
        return forumsService.getReplies(jwt.getSubject(), threadId, cursor, size);
    }

    /** One keyset page of a reply's direct children — called when the user expands a reply. */
    @GetMapping("/posts/{postId}/replies")
    public CursorPage<ReplyDto> getPostReplies(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID postId,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "20") int size) {
        return forumsService.getPostReplies(jwt.getSubject(), postId, cursor, size);
    }

    // ---- writes ------------------------------------------------------------

    @PostMapping("/threads")
    @ResponseStatus(HttpStatus.CREATED)
    public ThreadDetailDto createThread(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody CreateThreadRequest request) {
        return forumsService.createThread(jwt.getSubject(), request);
    }

    /** Edits the OP body of a thread the caller authored (content only; the title is immutable). */
    @PatchMapping("/threads/{threadId}")
    public ThreadDetailDto updateThread(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID threadId,
                                        @Valid @RequestBody UpdateThreadRequest request) {
        return forumsService.updateThread(jwt.getSubject(), threadId, request);
    }

    @PostMapping("/threads/{threadId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ReplyDto addReply(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID threadId,
                             @Valid @RequestBody CreateReplyRequest request) {
        return forumsService.addReply(jwt.getSubject(), threadId, request);
    }

    /** Edits the text of a reply the caller authored. */
    @PatchMapping("/posts/{postId}")
    public ReplyDto updateReply(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID postId,
                                @Valid @RequestBody UpdateReplyRequest request) {
        return forumsService.updateReply(jwt.getSubject(), postId, request);
    }

    /** Likes a thread (idempotent). */
    @PostMapping("/threads/{threadId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeThread(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID threadId) {
        forumsService.likeThread(jwt.getSubject(), threadId);
    }

    /** Removes the caller's like from a thread (idempotent). */
    @DeleteMapping("/threads/{threadId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeThread(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID threadId) {
        forumsService.unlikeThread(jwt.getSubject(), threadId);
    }

    /** Likes a reply (idempotent). */
    @PostMapping("/posts/{postId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likePost(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID postId) {
        forumsService.likePost(jwt.getSubject(), postId);
    }

    /** Removes the caller's like from a reply (idempotent). */
    @DeleteMapping("/posts/{postId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId) {
        forumsService.unlikePost(jwt.getSubject(), postId);
    }

    /**
     * Deletes a thread the caller authored: with replies it is anonymized (author hidden, content
     * and replies stay visible and repliable); with no replies it is removed outright.
     */
    @DeleteMapping("/threads/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteThread(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID threadId) {
        forumsService.deleteThread(jwt.getSubject(), threadId);
    }

    /** Deletes a reply the caller authored (soft if it has children, hard otherwise). */
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId) {
        forumsService.deletePost(jwt.getSubject(), postId);
    }
}

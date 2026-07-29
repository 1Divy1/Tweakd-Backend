package com.carsocialmedia.backend.forums.internal.controllers;

import com.carsocialmedia.backend.forums.ForumsService;
import com.carsocialmedia.backend.forums.dto.CursorPage;
import com.carsocialmedia.backend.forums.dto.ForumSuggestionDto;
import com.carsocialmedia.backend.forums.dto.ReplyDto;
import com.carsocialmedia.backend.forums.dto.ThreadCardDto;
import com.carsocialmedia.backend.forums.dto.ThreadDetailDto;
import com.carsocialmedia.backend.forums.dto.TopicDto;
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

    /** The active topics, in curated order. */
    @GetMapping("/topics")
    public List<TopicDto> getTopics() {
        return forumsService.listTopics();
    }

    /**
     * Popular-hub suggestions (brands and models) for the discovery / empty state, ranked by
     * thread count. {@code limit} is defaulted and capped by the service.
     */
    @GetMapping("/suggestions")
    public List<ForumSuggestionDto> getSuggestions(@RequestParam(defaultValue = "10") int limit) {
        return forumsService.getSuggestions(limit);
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

    /** Threads scoped to a brand, optionally refined by {@code topic}. */
    @GetMapping("/brands/{brandId}/threads")
    public CursorPage<ThreadCardDto> getBrandThreads(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID brandId,
                                                     @RequestParam(defaultValue = "hot") String sort,
                                                     @RequestParam(required = false) String topic,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return forumsService.getBrandThreads(jwt.getSubject(), brandId, sort, topic, cursor, size);
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


    // ---- thread detail & replies -------------------------------------------

    /** One keyset page of the caller's saved (bookmarked) threads, newest save first. */
    @GetMapping("/threads/saved")
    public CursorPage<ThreadCardDto> getSavedThreads(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return forumsService.getSavedThreads(jwt.getSubject(), cursor, size);
    }

    @GetMapping("/threads/{threadId}")
    public ThreadDetailDto getThread(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID threadId) {
        return forumsService.getThread(jwt.getSubject(), threadId);
    }

    /**
     * One keyset page of a thread's top-level replies (no children — expand on demand).
     * {@code sort} = {@code old} (default) | {@code new}.
     */
    @GetMapping("/threads/{threadId}/replies")
    public CursorPage<ReplyDto> getReplies(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID threadId,
                                           @RequestParam(defaultValue = "old") String sort,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "20") int size) {
        return forumsService.getReplies(jwt.getSubject(), threadId, sort, cursor, size);
    }

    /**
     * One keyset page of a reply's direct children — called when the user expands a reply.
     * {@code sort} = {@code old} (default) | {@code new}.
     */
    @GetMapping("/replies/{replyId}/replies")
    public CursorPage<ReplyDto> getPostReplies(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID replyId,
                                               @RequestParam(defaultValue = "old") String sort,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "20") int size) {
        return forumsService.getPostReplies(jwt.getSubject(), replyId, sort, cursor, size);
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
    @PatchMapping("/replies/{replyId}")
    public ReplyDto updateReply(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID replyId,
                                @Valid @RequestBody UpdateReplyRequest request) {
        return forumsService.updateReply(jwt.getSubject(), replyId, request);
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
    @PostMapping("/replies/{replyId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likePost(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID replyId) {
        forumsService.likePost(jwt.getSubject(), replyId);
    }

    /** Removes the caller's like from a reply (idempotent). */
    @DeleteMapping("/replies/{replyId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID replyId) {
        forumsService.unlikePost(jwt.getSubject(), replyId);
    }

    /** Saves (bookmarks) a thread for the caller (idempotent). */
    @PostMapping("/threads/{threadId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveThread(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID threadId) {
        forumsService.saveThread(jwt.getSubject(), threadId);
    }

    /** Removes the caller's save of a thread (idempotent). */
    @DeleteMapping("/threads/{threadId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsaveThread(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID threadId) {
        forumsService.unsaveThread(jwt.getSubject(), threadId);
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
    @DeleteMapping("/replies/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID replyId) {
        forumsService.deletePost(jwt.getSubject(), replyId);
    }
}

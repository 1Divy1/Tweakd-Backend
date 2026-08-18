package com.carsocialmedia.backend.feedbackfeed.internal;

import com.carsocialmedia.backend.feedbackfeed.FeedbackFeedService;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackCategoryDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedPageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedStatusDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackMessageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.request.FeedbackVoteRequest;
import com.carsocialmedia.backend.feedbackfeed.dto.request.SubmitFeedbackMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * The community feedback feed's user-facing endpoints: the feed itself, the completed-requests
 * section behind it, publishing, and voting. The dashboard operations live in the {@code admin}
 * module and reach this module through its service interface.
 */
@RestController
@RequestMapping("/api/v1/feedback-feed")
class FeedbackFeedController {

    private final FeedbackFeedService feedbackFeedService;

    FeedbackFeedController(FeedbackFeedService feedbackFeedService) {
        this.feedbackFeedService = feedbackFeedService;
    }

    /** The categories to pick from on the compose screen (bug / feature request / improvement). */
    @GetMapping("/types")
    public List<FeedbackCategoryDto> getTypes() {
        return feedbackFeedService.listTypes();
    }

    /** The roadmap stages, in order — for the status pills. */
    @GetMapping("/statuses")
    public List<FeedbackFeedStatusDto> getStatuses() {
        return feedbackFeedService.listStatuses();
    }

    /**
     * One page of the feed. {@code sort} = newest | popular | oldest (default newest). Shipped
     * messages are not included; they live under {@code /completed}.
     */
    @GetMapping
    public FeedbackFeedPageDto getFeed(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "newest") String sort,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(defaultValue = "20") int size) {
        return feedbackFeedService.getFeed(UUID.fromString(jwt.getSubject()), sort, cursor, size);
    }

    /** One page of the "Completed requests" section, most recently shipped first. */
    @GetMapping("/completed")
    public FeedbackFeedPageDto getCompleted(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "20") int size) {
        return feedbackFeedService.getCompleted(UUID.fromString(jwt.getSubject()), cursor, size);
    }

    /** A single feedback message, with the caller's own vote resolved. */
    @GetMapping("/{messageId}")
    public FeedbackMessageDto getMessage(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID messageId) {
        return feedbackFeedService.getMessage(UUID.fromString(jwt.getSubject()), messageId);
    }

    /** Publishes a message to the feed. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackMessageDto submit(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody SubmitFeedbackMessageRequest request) {
        return feedbackFeedService.submit(UUID.fromString(jwt.getSubject()), request);
    }

    /** Deletes the caller's own message, while it is still awaiting triage. */
    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID messageId) {
        feedbackFeedService.deleteOwn(UUID.fromString(jwt.getSubject()), messageId);
    }

    /**
     * Casts a vote ({@code value} = 1 or -1) and returns the message with fresh counts. Sending the
     * direction already held withdraws the vote; the opposite direction switches it.
     */
    @PostMapping("/{messageId}/vote")
    public FeedbackMessageDto vote(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID messageId,
                                   @Valid @RequestBody FeedbackVoteRequest request) {
        return feedbackFeedService.vote(UUID.fromString(jwt.getSubject()), messageId, request.value());
    }

    /** Withdraws the caller's vote, whichever way it went. Idempotent. */
    @DeleteMapping("/{messageId}/vote")
    public FeedbackMessageDto removeVote(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID messageId) {
        return feedbackFeedService.removeVote(UUID.fromString(jwt.getSubject()), messageId);
    }
}

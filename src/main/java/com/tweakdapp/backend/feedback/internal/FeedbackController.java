package com.tweakdapp.backend.feedback.internal;

import com.tweakdapp.backend.feedback.FeedbackService;
import com.tweakdapp.backend.feedback.dto.FeedbackBoardItemDto;
import com.tweakdapp.backend.feedback.dto.FeedbackBoardPageDto;
import com.tweakdapp.backend.feedback.dto.FeedbackCommentDto;
import com.tweakdapp.backend.feedback.dto.FeedbackCommentPageDto;
import com.tweakdapp.backend.feedback.dto.FeedbackCommentRequest;
import com.tweakdapp.backend.feedback.dto.FeedbackFeatureDto;
import com.tweakdapp.backend.feedback.dto.FeedbackRequest;
import com.tweakdapp.backend.feedback.dto.FeedbackStatusDto;
import com.tweakdapp.backend.feedback.dto.FeedbackTypeDto;
import com.tweakdapp.backend.feedback.dto.MyFeedbackDto;
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
 * The {@code feedback} module's user-facing endpoints: submitting feedback, the reference-option
 * lists that back the pickers, the caller's own submission history, and the public feedback board
 * (browse / vote / comment / subscribe). The admin-dashboard operations live in the {@code admin}
 * module and reach this module through its service interface.
 */
@RestController
@RequestMapping("/api/v1/feedback")
class FeedbackController {

    private final FeedbackService feedbackService;

    FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** Submits a piece of feedback. {@code content} and {@code type} are required. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitFeedback(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody FeedbackRequest request) {
        feedbackService.submitFeedback(UUID.fromString(jwt.getSubject()), request);
    }

    /** The feedback categories a user may pick from (bug / feature / general / praise). */
    @GetMapping("/types")
    public List<FeedbackTypeDto> getFeedbackTypes() {
        return feedbackService.listFeedbackTypes();
    }

    /** The app features a user may attach feedback to. */
    @GetMapping("/features")
    public List<FeedbackFeatureDto> getFeedbackFeatures() {
        return feedbackService.listFeedbackFeatures();
    }

    /** The status lifecycle (id, label, color) in roadmap order — for chips and filters. */
    @GetMapping("/statuses")
    public List<FeedbackStatusDto> getFeedbackStatuses() {
        return feedbackService.listFeedbackStatuses();
    }

    /** The feedback the authenticated user has submitted, newest first. */
    @GetMapping("/mine")
    public List<MyFeedbackDto> getMyFeedback(@AuthenticationPrincipal Jwt jwt) {
        return feedbackService.listMyFeedback(UUID.fromString(jwt.getSubject()));
    }

    // ------------------------------------------------------------------
    // Public feedback board
    // ------------------------------------------------------------------

    /** One page of the public board. {@code sort} = top | new | trending (default top). */
    @GetMapping("/board")
    public FeedbackBoardPageDto getBoard(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "top") String sort,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(defaultValue = "20") int size) {
        return feedbackService.getBoard(UUID.fromString(jwt.getSubject()), sort, type, status, cursor, size);
    }

    /** One feedback entry with the caller's voted / subscribed flags. */
    @GetMapping("/{feedbackId}")
    public FeedbackBoardItemDto getBoardItem(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID feedbackId) {
        return feedbackService.getBoardItem(UUID.fromString(jwt.getSubject()), feedbackId);
    }

    /** Upvotes a feedback. Idempotent. */
    @PostMapping("/{feedbackId}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID feedbackId) {
        feedbackService.vote(UUID.fromString(jwt.getSubject()), feedbackId);
    }

    /** Removes the caller's upvote. Idempotent. */
    @DeleteMapping("/{feedbackId}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unvote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID feedbackId) {
        feedbackService.unvote(UUID.fromString(jwt.getSubject()), feedbackId);
    }

    /** One page of a feedback's comments, newest first. */
    @GetMapping("/{feedbackId}/comments")
    public FeedbackCommentPageDto getComments(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID feedbackId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(defaultValue = "20") int size) {
        return feedbackService.getComments(UUID.fromString(jwt.getSubject()), feedbackId, cursor, size);
    }

    /** Adds a comment under a feedback entry. */
    @PostMapping("/{feedbackId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackCommentDto addComment(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID feedbackId,
                                         @Valid @RequestBody FeedbackCommentRequest request) {
        return feedbackService.addComment(UUID.fromString(jwt.getSubject()), feedbackId, request);
    }

    /** Deletes the caller's own comment. */
    @DeleteMapping("/{feedbackId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID feedbackId,
                              @PathVariable UUID commentId) {
        feedbackService.deleteComment(UUID.fromString(jwt.getSubject()), feedbackId, commentId);
    }

    /** Subscribes the caller to this feedback's status-change notifications. Idempotent. */
    @PostMapping("/{feedbackId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID feedbackId) {
        feedbackService.subscribe(UUID.fromString(jwt.getSubject()), feedbackId);
    }

    /** Removes the caller's subscription. Idempotent. */
    @DeleteMapping("/{feedbackId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID feedbackId) {
        feedbackService.unsubscribe(UUID.fromString(jwt.getSubject()), feedbackId);
    }
}

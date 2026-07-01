package com.carsocialmedia.backend.feedback.internal;

import com.carsocialmedia.backend.feedback.FeedbackService;
import com.carsocialmedia.backend.feedback.dto.FeedbackFeatureDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackTypeDto;
import com.carsocialmedia.backend.feedback.dto.MyFeedbackDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * All of the {@code feedback} module's endpoints: submitting feedback, the two reference-option lists
 * that back the pickers, and the caller's own submission history. Everything is scoped to the JWT
 * subject, so a user only ever sees their own feedback.
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

    /** The feedback categories a user may pick from (bug / feature / general). */
    @GetMapping("/types")
    public List<FeedbackTypeDto> getFeedbackTypes() {
        return feedbackService.listFeedbackTypes();
    }

    /** The app features a user may attach feedback to. */
    @GetMapping("/features")
    public List<FeedbackFeatureDto> getFeedbackFeatures() {
        return feedbackService.listFeedbackFeatures();
    }

    /** The feedback the authenticated user has submitted, newest first. */
    @GetMapping("/mine")
    public List<MyFeedbackDto> getMyFeedback(@AuthenticationPrincipal Jwt jwt) {
        return feedbackService.listMyFeedback(UUID.fromString(jwt.getSubject()));
    }
}

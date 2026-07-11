package com.carsocialmedia.backend.admin.internal.controllers;

import com.carsocialmedia.backend.admin.internal.AdminAccessService;
import com.carsocialmedia.backend.admin.internal.AdminFeedbackService;
import com.carsocialmedia.backend.admin.internal.Capability;
import com.carsocialmedia.backend.admin.internal.dto.FeedbackResponseRequest;
import com.carsocialmedia.backend.admin.internal.dto.UpdateFeedbackStatusRequest;
import com.carsocialmedia.backend.feedback.FeedbackService;
import com.carsocialmedia.backend.feedback.dto.AdminFeedbackDto;
import com.carsocialmedia.backend.feedback.dto.AdminFeedbackPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatsDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Feedback page of the dashboard. Reads and the official response go straight to the feedback
 * module; the status change goes through {@link AdminFeedbackService} for the subscriber
 * notification fan-out.
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
class AdminFeedbackController {

    private final AdminAccessService access;
    private final FeedbackService feedbackService;
    private final AdminFeedbackService adminFeedbackService;

    AdminFeedbackController(AdminAccessService access,
                            FeedbackService feedbackService,
                            AdminFeedbackService adminFeedbackService) {
        this.access = access;
        this.feedbackService = feedbackService;
        this.adminFeedbackService = adminFeedbackService;
    }

    /** One keyset page of all feedback; same sorts/filters as the public board. */
    @GetMapping
    public AdminFeedbackPageDto listFeedback(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "top") String sort,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "20") int size) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return feedbackService.listAllFeedback(sort, type, status, cursor, size);
    }

    @GetMapping("/stats")
    public FeedbackStatsDto getStats(@AuthenticationPrincipal Jwt jwt) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return feedbackService.getStats();
    }

    /** The status lifecycle options, for the dashboard's status picker. */
    @GetMapping("/statuses")
    public List<FeedbackStatusDto> listStatuses(@AuthenticationPrincipal Jwt jwt) {
        access.requireMember(UUID.fromString(jwt.getSubject()));
        return feedbackService.listFeedbackStatuses();
    }

    /** Writes (or replaces) the team's official response shown on the board. */
    @PostMapping("/{feedbackId}/response")
    public AdminFeedbackDto respond(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID feedbackId,
                                    @Valid @RequestBody FeedbackResponseRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return feedbackService.respond(feedbackId, request.response());
    }

    /** Moves the feedback through its lifecycle and notifies the author + subscribers in-app. */
    @PatchMapping("/{feedbackId}/status")
    public FeedbackStatusDto updateStatus(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID feedbackId,
                                          @Valid @RequestBody UpdateFeedbackStatusRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return adminFeedbackService.updateStatus(feedbackId, request.status());
    }
}

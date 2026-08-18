package com.carsocialmedia.backend.admin.internal.controllers;

import com.carsocialmedia.backend.admin.internal.AdminAccessService;
import com.carsocialmedia.backend.admin.internal.Capability;
import com.carsocialmedia.backend.feedbackfeed.FeedbackFeedService;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedPageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedStatusDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackMessageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.request.FeedbackStaffResponseRequest;
import com.carsocialmedia.backend.feedbackfeed.dto.request.UpdateFeedbackFeedStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Community Feedback page of the dashboard: the full list, the most-voted panel, and the three
 * staff writes (status, official response, removal).
 *
 * <p>Everything here is gated on {@link Capability#MANAGE_ROADMAP} — owner and senior admin only.
 * Announcing that a request is in development or shipped is a product statement rather than user
 * support, so support agents deliberately do not get it (they keep {@code ANSWER_TICKETS} for the
 * older feedback board).
 *
 * <p>Unlike {@link AdminFeedbackController} there is no notification fan-out to orchestrate: the
 * author's status-change notification is written by a Supabase trigger, so the calls pass straight
 * through to the feedback-feed module.
 */
@RestController
@RequestMapping("/api/v1/admin/feedback-feed")
class AdminFeedbackFeedController {

    private final AdminAccessService access;
    private final FeedbackFeedService feedbackFeedService;

    AdminFeedbackFeedController(AdminAccessService access, FeedbackFeedService feedbackFeedService) {
        this.access = access;
        this.feedbackFeedService = feedbackFeedService;
    }

    /**
     * One keyset page of all feedback, newest first — every status, optionally narrowed by category
     * or status, with staff-removed messages included on request.
     */
    @GetMapping
    public FeedbackFeedPageDto list(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(name = "include_removed", defaultValue = "false")
                                    boolean includeRemoved,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "20") int size) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_ROADMAP);
        return feedbackFeedService.listAll(type, status, includeRemoved, cursor, size);
    }

    /**
     * The most-voted requests that have not shipped yet, highest net votes first — the panel the
     * owner picks the next batch of work from. Nothing is persisted by looking at it.
     */
    @GetMapping("/top")
    public List<FeedbackMessageDto> topVoted(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "3") int limit) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_ROADMAP);
        return feedbackFeedService.listTopVoted(limit);
    }

    /** The roadmap stages, for the dashboard's status picker. */
    @GetMapping("/statuses")
    public List<FeedbackFeedStatusDto> statuses(@AuthenticationPrincipal Jwt jwt) {
        access.requireMember(UUID.fromString(jwt.getSubject()));
        return feedbackFeedService.listStatuses();
    }

    /** Moves a message along the roadmap. The author is notified by a DB trigger. */
    @PatchMapping("/{messageId}/status")
    public FeedbackMessageDto updateStatus(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID messageId,
                                           @Valid @RequestBody UpdateFeedbackFeedStatusRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_ROADMAP);
        return feedbackFeedService.updateStatus(messageId, request.status());
    }

    /**
     * Writes, replaces or (with an empty body) clears the team's official reply. Silent by design —
     * only a status change notifies the author.
     */
    @PutMapping("/{messageId}/response")
    public FeedbackMessageDto respond(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID messageId,
                                      @Valid @RequestBody FeedbackStaffResponseRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_ROADMAP);
        return feedbackFeedService.respond(messageId, request.response());
    }

    /** Removes spam or abuse from the feed, keeping the row for audit. Idempotent. */
    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID messageId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_ROADMAP);
        feedbackFeedService.removeAsStaff(messageId);
    }
}

package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.AdminModerationService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.admin.internal.dto.CaseDetailDto;
import com.tweakdapp.backend.admin.internal.dto.CasePageDto;
import com.tweakdapp.backend.admin.internal.dto.ModerationDecisionRequest;
import com.tweakdapp.backend.admin.internal.dto.QueueCountsDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Content Moderation page. Queue and decisions need REVIEW_CONTENT; warn / ban / unban
 * additionally need WARN_BAN. Decision bodies are optional — an empty POST is a decision with no
 * note.
 */
@RestController
@RequestMapping("/api/v1/admin/moderation")
class AdminModerationController {

    private final AdminAccessService access;
    private final AdminModerationService moderationService;

    AdminModerationController(AdminAccessService access, AdminModerationService moderationService) {
        this.access = access;
        this.moderationService = moderationService;
    }

    /**
     * One keyset page of the queue. {@code status}: {@code open} / {@code escalated} /
     * {@code resolved}; omitted = everything pending. {@code type} filters by target type.
     */
    @GetMapping("/queue")
    public CasePageDto getQueue(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String cursor,
                                @RequestParam(defaultValue = "20") int size) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.REVIEW_CONTENT);
        return moderationService.getQueue(status, type, cursor, size);
    }

    @GetMapping("/queue/counts")
    public QueueCountsDto getQueueCounts(@AuthenticationPrincipal Jwt jwt) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.REVIEW_CONTENT);
        return moderationService.getQueueCounts();
    }

    @GetMapping("/cases/{caseId}")
    public CaseDetailDto getCase(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.REVIEW_CONTENT);
        return moderationService.getCase(caseId);
    }

    /** Content is fine — dismisses the reports and closes the case. */
    @PostMapping("/cases/{caseId}/approve")
    public CaseDetailDto approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                 @Valid @RequestBody(required = false) ModerationDecisionRequest request) {
        UUID moderatorId = UUID.fromString(jwt.getSubject());
        access.require(moderatorId, Capability.REVIEW_CONTENT);
        moderationService.approve(moderatorId, caseId, note(request));
        return moderationService.getCase(caseId);
    }

    /** Removes the content (hard delete through its module) and notifies the author. */
    @PostMapping("/cases/{caseId}/remove")
    public CaseDetailDto remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                @Valid @RequestBody(required = false) ModerationDecisionRequest request) {
        UUID moderatorId = UUID.fromString(jwt.getSubject());
        access.require(moderatorId, Capability.REVIEW_CONTENT);
        moderationService.removeContent(moderatorId, caseId, note(request));
        return moderationService.getCase(caseId);
    }

    /** Sends the author a formal warning ({@code note} is the message, required). */
    @PostMapping("/cases/{caseId}/warn")
    public CaseDetailDto warn(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                              @Valid @RequestBody(required = false) ModerationDecisionRequest request) {
        UUID moderatorId = UUID.fromString(jwt.getSubject());
        access.require(moderatorId, Capability.WARN_BAN);
        moderationService.warn(moderatorId, caseId, note(request));
        return moderationService.getCase(caseId);
    }

    /** Bans the author — temp when {@code banDays} is set, permanent otherwise. */
    @PostMapping("/cases/{caseId}/ban")
    public CaseDetailDto ban(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                             @Valid @RequestBody(required = false) ModerationDecisionRequest request) {
        UUID moderatorId = UUID.fromString(jwt.getSubject());
        access.require(moderatorId, Capability.WARN_BAN);
        moderationService.ban(moderatorId, caseId, note(request),
                request == null ? null : request.banDays());
        return moderationService.getCase(caseId);
    }

    /** Flags the case for a senior admin. */
    @PostMapping("/cases/{caseId}/escalate")
    public CaseDetailDto escalate(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                  @Valid @RequestBody(required = false) ModerationDecisionRequest request) {
        UUID moderatorId = UUID.fromString(jwt.getSubject());
        access.require(moderatorId, Capability.REVIEW_CONTENT);
        moderationService.escalate(moderatorId, caseId, note(request));
        return moderationService.getCase(caseId);
    }

    /** Lifts a user's ban (the author panel's unban button; not tied to a case). */
    @PostMapping("/users/{profileId}/unban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId,
                      @Valid @RequestBody(required = false) ModerationDecisionRequest request) {
        UUID moderatorId = UUID.fromString(jwt.getSubject());
        access.require(moderatorId, Capability.WARN_BAN);
        moderationService.unban(moderatorId, profileId, note(request));
    }

    private String note(ModerationDecisionRequest request) {
        return request == null ? null : request.note();
    }
}

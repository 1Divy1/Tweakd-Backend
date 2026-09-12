package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.AdminContestDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contest oversight. Since the scheduler was removed (see {@code CONTESTS_MANUAL_LIFECYCLE.md}) a
 * contest opens and finishes only when an organizer taps, or when the event itself finishes. An
 * organizer who walks away leaves a contest taking votes indefinitely, with nobody in the app able
 * to end it — this page is the safety valve.
 *
 * <p>Gated on {@link Capability#MANAGE_CONTESTS}: owner and senior admin only. Finishing a contest
 * pays out reputation and badges that cannot be cleanly taken back, so it sits with the same people
 * who publish events, not with content moderators.
 *
 * <p>Read-only apart from the force-finish. Nothing here creates, edits or deletes a contest —
 * those stay the organizer's, in the app.
 */
@RestController
@RequestMapping("/api/v1/admin/contests")
class AdminContestsController {

    private final AdminAccessService access;
    private final MapEventContestsService contestsService;

    AdminContestsController(AdminAccessService access, MapEventContestsService contestsService) {
        this.access = access;
        this.contestsService = contestsService;
    }

    /**
     * Contests in one status across every event, longest-running first. {@code status} defaults to
     * {@code open} — the worklist; pass {@code scheduled} or {@code finished} to look around.
     */
    @GetMapping
    public List<AdminContestDto> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "50") int limit) {
        access.require(userId(jwt), Capability.MANAGE_CONTESTS);
        return contestsService.listContestsForReview(status, limit);
    }

    /** How many contests are currently open — the dashboard badge. */
    @GetMapping("/counts")
    public Map<String, Long> counts(@AuthenticationPrincipal Jwt jwt) {
        access.require(userId(jwt), Capability.MANAGE_CONTESTS);
        return Map.of("open", contestsService.countOpenContests());
    }

    /**
     * Force-finishes an open contest: standings freeze and the podium is paid, exactly as the
     * organizer's own "finish now". Idempotent.
     */
    @PostMapping("/{contestId}/finish")
    public AdminContestDto finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contestId) {
        UUID staffId = userId(jwt);
        access.require(staffId, Capability.MANAGE_CONTESTS);
        return contestsService.finishContestAsAdmin(contestId, staffId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

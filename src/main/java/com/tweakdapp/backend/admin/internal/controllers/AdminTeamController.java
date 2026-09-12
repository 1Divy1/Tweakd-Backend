package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.AdminTeamService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.admin.internal.dto.AddTeamMemberRequest;
import com.tweakdapp.backend.admin.internal.dto.TeamMemberDto;
import com.tweakdapp.backend.admin.internal.dto.UpdateTeamMemberRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The Moderators page. Viewing needs team membership; changing anything needs MANAGE_TEAM. */
@RestController
@RequestMapping("/api/v1/admin/team")
class AdminTeamController {

    private final AdminAccessService access;
    private final AdminTeamService teamService;

    AdminTeamController(AdminAccessService access, AdminTeamService teamService) {
        this.access = access;
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamMemberDto> listTeam(@AuthenticationPrincipal Jwt jwt) {
        access.requireMember(UUID.fromString(jwt.getSubject()));
        return teamService.listTeam();
    }

    /** Invites a new staff member by email (staff accounts are separate from app accounts). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberDto addMember(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody AddTeamMemberRequest request) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        access.require(callerId, Capability.MANAGE_TEAM);
        return teamService.addMember(callerId, request);
    }

    /** Changes a member's role (never to / from owner). */
    @PatchMapping("/{memberId}")
    public TeamMemberDto updateMember(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID memberId,
                                      @Valid @RequestBody UpdateTeamMemberRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_TEAM);
        return teamService.updateMember(memberId, request);
    }

    /**
     * Hands ownership to another team member; the caller steps down to {@code senior_admin}. Owner
     * only, by way of {@code TRANSFER_OWNERSHIP} — the one capability {@code senior_admin} lacks.
     *
     * <p>Returns the new owner's row. The caller's own row has changed too, so the dashboard
     * refetches the team afterwards rather than patching one row into its state.
     */
    @PostMapping("/{memberId}/transfer-ownership")
    public TeamMemberDto transferOwnership(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID memberId) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        access.require(callerId, Capability.TRANSFER_OWNERSHIP);
        return teamService.transferOwnership(callerId, memberId);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID memberId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_TEAM);
        teamService.removeMember(memberId);
    }
}

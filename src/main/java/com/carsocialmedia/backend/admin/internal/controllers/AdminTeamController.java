package com.carsocialmedia.backend.admin.internal.controllers;

import com.carsocialmedia.backend.admin.internal.AdminAccessService;
import com.carsocialmedia.backend.admin.internal.AdminTeamService;
import com.carsocialmedia.backend.admin.internal.Capability;
import com.carsocialmedia.backend.admin.internal.dto.AddTeamMemberRequest;
import com.carsocialmedia.backend.admin.internal.dto.TeamMemberDto;
import com.carsocialmedia.backend.admin.internal.dto.UpdateTeamMemberRequest;
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

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID memberId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_TEAM);
        teamService.removeMember(memberId);
    }
}

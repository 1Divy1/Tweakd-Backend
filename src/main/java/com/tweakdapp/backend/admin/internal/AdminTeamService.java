package com.tweakdapp.backend.admin.internal;

import com.tweakdapp.backend.admin.exception.AlreadyTeamMemberException;
import com.tweakdapp.backend.admin.exception.CannotModifyOwnerException;
import com.tweakdapp.backend.admin.exception.InvalidTeamRoleException;
import com.tweakdapp.backend.admin.exception.TeamMemberNotFoundException;
import com.tweakdapp.backend.admin.internal.dto.AddTeamMemberRequest;
import com.tweakdapp.backend.admin.internal.dto.TeamMemberDto;
import com.tweakdapp.backend.admin.internal.dto.UpdateTeamMemberRequest;
import com.tweakdapp.backend.admin.internal.entities.AdminTeamMemberEntity;
import com.tweakdapp.backend.admin.internal.repositories.AdminTeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Moderators page. Staff accounts are separate from app accounts: a new member is invited by
 * email — {@link SupabaseAuthAdminClient} creates a profile-less Supabase auth user and sends the
 * invite mail — and their identity (email / display name / avatar) lives on the team row itself.
 * The {@code owner} role is never granted here (only via a future ownership-transfer endpoint) and
 * the owner's row can be neither re-roled nor removed.
 */
@Service
public class AdminTeamService {

    private final AdminTeamMemberRepository teamRepository;
    private final SupabaseAuthAdminClient authAdmin;

    AdminTeamService(AdminTeamMemberRepository teamRepository, SupabaseAuthAdminClient authAdmin) {
        this.teamRepository = teamRepository;
        this.authAdmin = authAdmin;
    }

    @Transactional(readOnly = true)
    public List<TeamMemberDto> listTeam() {
        return teamRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(AdminTeamService::toDto)
                .toList();
    }

    /**
     * Invites a staff member: Supabase creates the auth user (no app profile) and emails the
     * invite; the row stays {@code invited} until their first admin API call. Not transactional —
     * the Supabase call is the point of no return, so the row insert comes after it and a failed
     * insert is repaired by re-inviting (the 409 then points at the half-created auth user).
     */
    public TeamMemberDto addMember(UUID callerId, AddTeamMemberRequest request) {
        String role = validateAssignableRole(request.role());
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (teamRepository.existsByEmail(email)) {
            throw new AlreadyTeamMemberException(email);
        }

        UUID userId = authAdmin.inviteStaff(email, request.displayName().strip());

        AdminTeamMemberEntity member = new AdminTeamMemberEntity();
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("invited");
        member.setEmail(email);
        member.setDisplayName(request.displayName().strip());
        member.setInvitedBy(callerId);
        teamRepository.saveAndFlush(member);
        return toDto(member);
    }

    @Transactional
    public TeamMemberDto updateMember(UUID memberId, UpdateTeamMemberRequest request) {
        String role = validateAssignableRole(request.role());
        AdminTeamMemberEntity member = teamRepository.findById(memberId)
                .orElseThrow(() -> new TeamMemberNotFoundException(memberId));
        if (AdminRole.owner.name().equals(member.getRole())) {
            throw new CannotModifyOwnerException();
        }
        member.setRole(role);
        teamRepository.save(member);
        return toDto(member);
    }

    /**
     * Removes the team row and deletes the staff auth user, so the login stops working too (an
     * already-issued JWT passes the security gate until it expires, but every endpoint 403s on the
     * missing team row). Row first: if the Supabase call fails, the transaction restores the row.
     */
    @Transactional
    public void removeMember(UUID memberId) {
        AdminTeamMemberEntity member = teamRepository.findById(memberId)
                .orElseThrow(() -> new TeamMemberNotFoundException(memberId));
        if (AdminRole.owner.name().equals(member.getRole())) {
            throw new CannotModifyOwnerException();
        }
        teamRepository.delete(member);
        teamRepository.flush();
        authAdmin.deleteUser(memberId);
    }

    /** Any known role except {@code owner} may be handed out. */
    private String validateAssignableRole(String role) {
        boolean known = Arrays.stream(AdminRole.values()).anyMatch(r -> r.name().equals(role));
        if (!known || AdminRole.owner.name().equals(role)) {
            throw new InvalidTeamRoleException("Not an assignable team role: " + role);
        }
        return role;
    }

    private static TeamMemberDto toDto(AdminTeamMemberEntity member) {
        return new TeamMemberDto(
                member.getUserId(),
                member.getEmail(),
                member.getDisplayName(),
                member.getAvatarUrl(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getLastActiveAt());
    }
}

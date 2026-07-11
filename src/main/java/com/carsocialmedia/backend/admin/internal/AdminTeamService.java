package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.admin.exception.AlreadyTeamMemberException;
import com.carsocialmedia.backend.admin.exception.CannotModifyOwnerException;
import com.carsocialmedia.backend.admin.exception.InvalidTeamRoleException;
import com.carsocialmedia.backend.admin.exception.TeamMemberNotFoundException;
import com.carsocialmedia.backend.admin.internal.dto.AddTeamMemberRequest;
import com.carsocialmedia.backend.admin.internal.dto.TeamMemberDto;
import com.carsocialmedia.backend.admin.internal.dto.UpdateTeamMemberRequest;
import com.carsocialmedia.backend.admin.internal.entities.AdminTeamMemberEntity;
import com.carsocialmedia.backend.admin.internal.repositories.AdminTeamMemberRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Moderators page. Members are existing app users added by username; the {@code owner} role is
 * never granted here (only via a future ownership-transfer endpoint) and the owner's row can be
 * neither re-roled nor removed.
 */
@Service
public class AdminTeamService {

    private final AdminTeamMemberRepository teamRepository;
    private final ProfileService profileService;

    AdminTeamService(AdminTeamMemberRepository teamRepository, ProfileService profileService) {
        this.teamRepository = teamRepository;
        this.profileService = profileService;
    }

    @Transactional(readOnly = true)
    public List<TeamMemberDto> listTeam() {
        List<AdminTeamMemberEntity> members = teamRepository.findAllByOrderByCreatedAtAsc();
        Map<UUID, ProfileSearchResultDto> profiles = profileService
                .findByIds(members.stream().map(AdminTeamMemberEntity::getUserId).toList()).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
        return members.stream()
                .map(member -> toDto(member, profiles.get(member.getUserId())))
                .toList();
    }

    @Transactional
    public TeamMemberDto addMember(UUID callerId, AddTeamMemberRequest request) {
        String role = validateAssignableRole(request.role());
        UUID userId = profileService.findIdByUsername(request.username())
                .orElseThrow(() -> ProfileNotFoundException.byUsername(request.username()));
        if (teamRepository.existsById(userId)) {
            throw new AlreadyTeamMemberException(request.username());
        }

        AdminTeamMemberEntity member = new AdminTeamMemberEntity();
        member.setUserId(userId);
        member.setRole(role);
        // No email-invite flow yet — the user already exists, so the row starts active. What still
        // gates their dashboard access is app_metadata.role = 'admin' on their Supabase user.
        member.setStatus("active");
        member.setInvitedBy(callerId);
        teamRepository.save(member);
        teamRepository.flush();

        ProfileSearchResultDto profile = profileService.findByIds(List.of(userId)).stream()
                .findFirst().orElse(null);
        return toDto(member, profile);
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

        ProfileSearchResultDto profile = profileService.findByIds(List.of(memberId)).stream()
                .findFirst().orElse(null);
        return toDto(member, profile);
    }

    @Transactional
    public void removeMember(UUID memberId) {
        AdminTeamMemberEntity member = teamRepository.findById(memberId)
                .orElseThrow(() -> new TeamMemberNotFoundException(memberId));
        if (AdminRole.owner.name().equals(member.getRole())) {
            throw new CannotModifyOwnerException();
        }
        teamRepository.delete(member);
    }

    /** Any known role except {@code owner} may be handed out. */
    private String validateAssignableRole(String role) {
        boolean known = Arrays.stream(AdminRole.values()).anyMatch(r -> r.name().equals(role));
        if (!known || AdminRole.owner.name().equals(role)) {
            throw new InvalidTeamRoleException("Not an assignable team role: " + role);
        }
        return role;
    }

    private TeamMemberDto toDto(AdminTeamMemberEntity member, ProfileSearchResultDto profile) {
        return new TeamMemberDto(
                member.getUserId(),
                profile == null ? null : profile.username(),
                profile == null ? null : profile.avatarUrl(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getLastActiveAt());
    }
}

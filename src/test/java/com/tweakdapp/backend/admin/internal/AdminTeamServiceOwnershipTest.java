package com.tweakdapp.backend.admin.internal;

import com.tweakdapp.backend.admin.exception.InvalidOwnershipTransferException;
import com.tweakdapp.backend.admin.exception.TeamMemberNotFoundException;
import com.tweakdapp.backend.admin.internal.dto.TeamMemberDto;
import com.tweakdapp.backend.admin.internal.entities.AdminTeamMemberEntity;
import com.tweakdapp.backend.admin.internal.repositories.AdminTeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ownership transfer — the only path by which {@code owner} is ever granted.
 *
 * <p>Two things are pinned rather than left to review. Both rows are taken {@code FOR UPDATE}, so
 * two transfers racing serialise instead of both demoting the owner and leaving the dashboard with
 * nobody who can transfer, re-role or remove anyone. And the demote is flushed <em>before</em> the
 * promote, because {@code admin_team_members_single_owner_idx} is checked per statement: promoting
 * first fails on an index the other order never touches.
 */
class AdminTeamServiceOwnershipTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private AdminTeamMemberRepository repository;
    private AdminTeamService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdminTeamMemberRepository.class);
        service = new AdminTeamService(repository, mock(SupabaseAuthAdminClient.class));
    }

    @Test
    void theOwnerStepsDownAsTheTargetIsPromoted() {
        AdminTeamMemberEntity owner = member(OWNER_ID, AdminRole.owner);
        AdminTeamMemberEntity target = member(TARGET_ID, AdminRole.content_moderator);
        when(repository.lockById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(repository.lockById(TARGET_ID)).thenReturn(Optional.of(target));

        TeamMemberDto result = service.transferOwnership(OWNER_ID, TARGET_ID);

        assertThat(result.userId()).isEqualTo(TARGET_ID);
        assertThat(result.role()).isEqualTo("owner");
        assertThat(owner.getRole()).isEqualTo("senior_admin");

        // Demote first: the single-owner index is a per-statement check.
        InOrder order = inOrder(repository);
        order.verify(repository).saveAndFlush(owner);
        order.verify(repository).saveAndFlush(target);
    }

    @Test
    void bothRowsAreLockedBeforeAnythingChanges() {
        when(repository.lockById(OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, AdminRole.owner)));
        when(repository.lockById(TARGET_ID)).thenReturn(Optional.of(member(TARGET_ID, AdminRole.technical)));

        service.transferOwnership(OWNER_ID, TARGET_ID);

        verify(repository).lockById(OWNER_ID);
        verify(repository).lockById(TARGET_ID);
    }

    @Test
    void onlyTheActualOwnerRowMayTransfer() {
        // The capability check says "owner", but the team row is the source of truth: a stale
        // capability decision must not be able to hand out the role.
        when(repository.lockById(OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, AdminRole.senior_admin)));
        when(repository.lockById(TARGET_ID)).thenReturn(Optional.of(member(TARGET_ID, AdminRole.technical)));

        assertThatThrownBy(() -> service.transferOwnership(OWNER_ID, TARGET_ID))
                .isInstanceOf(InvalidOwnershipTransferException.class);

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferringToYourselfIsRefusedBeforeAnyLockIsTaken() {
        assertThatThrownBy(() -> service.transferOwnership(OWNER_ID, OWNER_ID))
                .isInstanceOf(InvalidOwnershipTransferException.class);

        verify(repository, never()).lockById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anUnknownTargetIsANotFound() {
        when(repository.lockById(OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, AdminRole.owner)));
        when(repository.lockById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferOwnership(OWNER_ID, TARGET_ID))
                .isInstanceOf(TeamMemberNotFoundException.class);
    }

    private static AdminTeamMemberEntity member(UUID id, AdminRole role) {
        AdminTeamMemberEntity member = new AdminTeamMemberEntity();
        member.setUserId(id);
        member.setRole(role.name());
        member.setStatus("active");
        member.setEmail(id + "@tweakdapp.com");
        member.setDisplayName("Staff " + id);
        return member;
    }
}

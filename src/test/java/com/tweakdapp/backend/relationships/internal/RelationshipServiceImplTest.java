package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.relationships.dto.FollowProfileSearchResult;
import com.tweakdapp.backend.relationships.dto.FollowStatus;
import com.tweakdapp.backend.relationships.dto.FollowStatusDto;
import com.tweakdapp.backend.relationships.exception.CannotFollowSelfException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure-unit behavior of {@link RelationshipServiceImpl} with the repository and
 * {@link ProfileService} mocked: the self-follow guard, unknown-username resolution, idempotent
 * follow/unfollow, the {@code accepted}→{@link FollowStatus} mapping, follower/following list
 * hydration (viewer follow-state flag, order preservation, missing-profile filtering), and
 * remove-follower row addressing.
 */
@ExtendWith(MockitoExtension.class)
class RelationshipServiceImplTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID F1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID F2 = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    @Mock
    private RelationshipRepository relationshipRepository;
    @Mock
    private ProfileService profileService;
    @InjectMocks
    private RelationshipServiceImpl service;

    private static RelationshipEntity acceptedRow(UUID follower, UUID following) {
        RelationshipEntity e = new RelationshipEntity();
        e.setId(new RelationshipId(follower, following));
        e.setStatus("accepted");
        return e;
    }

    // ---- follow -------------------------------------------------------------

    @Test
    void followPersistsAnAcceptedRowAndReturnsAccepted() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.empty());

        FollowStatusDto result = service.follow(VIEWER.toString(), "target");

        assertThat(result.status()).isEqualTo(FollowStatus.ACCEPTED);
        ArgumentCaptor<RelationshipEntity> saved = ArgumentCaptor.forClass(RelationshipEntity.class);
        verify(relationshipRepository).save(saved.capture());
        assertThat(saved.getValue().getId().getFollowerId()).isEqualTo(VIEWER);
        assertThat(saved.getValue().getId().getFollowingId()).isEqualTo(TARGET);
        assertThat(saved.getValue().getStatus()).isEqualTo("accepted");
    }

    @Test
    void followingYourselfIsRejectedBeforePersistence() {
        when(profileService.findVisibleIdByUsername(VIEWER, "me")).thenReturn(Optional.of(VIEWER));

        assertThatExceptionOfType(CannotFollowSelfException.class)
                .isThrownBy(() -> service.follow(VIEWER.toString(), "me"));

        verify(relationshipRepository, never()).save(any());
        verify(relationshipRepository, never()).findById(any());
    }

    @Test
    void followingAnUnknownUsernameThrowsProfileNotFound() {
        when(profileService.findVisibleIdByUsername(VIEWER, "ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.follow(VIEWER.toString(), "ghost"));

        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void reFollowingIsIdempotentAndReturnsTheExistingStatusWithoutSaving() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.of(acceptedRow(VIEWER, TARGET)));

        FollowStatusDto result = service.follow(VIEWER.toString(), "target");

        assertThat(result.status()).isEqualTo(FollowStatus.ACCEPTED);
        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void anExistingNonAcceptedRowMapsToNotFollowing() {
        RelationshipEntity pending = acceptedRow(VIEWER, TARGET);
        pending.setStatus("pending");
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.of(pending));

        FollowStatusDto result = service.follow(VIEWER.toString(), "target");

        assertThat(result.status()).isEqualTo(FollowStatus.NOT_FOLLOWING);
        verify(relationshipRepository, never()).save(any());
    }

    // ---- unfollow -----------------------------------------------------------

    @Test
    void followingAnAccountHiddenByABlockReadsAsNotFound() {
        when(profileService.findVisibleIdByUsername(VIEWER, "blocked")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.follow(VIEWER.toString(), "blocked"));
        verifyNoInteractions(relationshipRepository);
    }

    @Test
    void unfollowDeletesTheExistingRow() {
        RelationshipEntity row = acceptedRow(VIEWER, TARGET);
        when(profileService.findIdByUsername("target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.of(row));

        service.unfollow(VIEWER.toString(), "target");

        verify(relationshipRepository).delete(row);
    }

    @Test
    void unfollowIsANoOpWhenNotFollowing() {
        when(profileService.findIdByUsername("target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.empty());

        service.unfollow(VIEWER.toString(), "target");

        verify(relationshipRepository, never()).delete(any());
    }

    @Test
    void unfollowingAnUnknownUsernameThrowsProfileNotFound() {
        when(profileService.findIdByUsername("ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.unfollow(VIEWER.toString(), "ghost"));
    }

    // ---- getFollowStatus ----------------------------------------------------

    @Test
    void getFollowStatusReturnsAcceptedWhenARowExists() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.of(acceptedRow(VIEWER, TARGET)));

        assertThat(service.getFollowStatus(VIEWER.toString(), "target").status())
                .isEqualTo(FollowStatus.ACCEPTED);
    }

    @Test
    void getFollowStatusReturnsNotFollowingWhenNoRowExists() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findById(new RelationshipId(VIEWER, TARGET)))
                .thenReturn(Optional.empty());

        assertThat(service.getFollowStatus(VIEWER.toString(), "target").status())
                .isEqualTo(FollowStatus.NOT_FOLLOWING);
    }

    // ---- getFollowers / getFollowing ---------------------------------------

    @Test
    void getFollowersHydratesInQueryOrderAndFlagsViewerFollowState() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findAcceptedFollowerIds(TARGET)).thenReturn(List.of(F1, F2));
        when(profileService.findByIds(List.of(F1, F2))).thenReturn(List.of(
                new ProfileSearchResultDto(F1, "Alice", "alice", "alice.png"),
                new ProfileSearchResultDto(F2, "Bob", "bob", "bob.png")));
        // Viewer already follows F2, not F1.
        when(relationshipRepository.findAcceptedFollowingIdsIn(VIEWER, List.of(F1, F2)))
                .thenReturn(List.of(F2));

        List<FollowProfileSearchResult> result = service.getFollowers(VIEWER.toString(), "target");

        assertThat(result).containsExactly(
                new FollowProfileSearchResult(F1, "alice", "alice.png", false),
                new FollowProfileSearchResult(F2, "bob", "bob.png", true));
    }

    @Test
    void getFollowersReturnsEmptyWithoutHydratingWhenTargetHasNoFollowers() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findAcceptedFollowerIds(TARGET)).thenReturn(List.of());

        assertThat(service.getFollowers(VIEWER.toString(), "target")).isEmpty();

        verify(profileService, never()).findByIds(any());
        verify(relationshipRepository, never()).findAcceptedFollowingIdsIn(any(), anyList());
    }

    @Test
    void getFollowingDelegatesToTheFollowingQuery() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findAcceptedFollowingIds(TARGET)).thenReturn(List.of(F1));
        when(profileService.findByIds(List.of(F1))).thenReturn(List.of(
                new ProfileSearchResultDto(F1, "Alice", "alice", "alice.png")));
        when(relationshipRepository.findAcceptedFollowingIdsIn(VIEWER, List.of(F1)))
                .thenReturn(List.of());

        List<FollowProfileSearchResult> result = service.getFollowing(VIEWER.toString(), "target");

        assertThat(result).containsExactly(
                new FollowProfileSearchResult(F1, "alice", "alice.png", false));
    }

    @Test
    void listEntriesWithNoHydratedProfileAreFilteredOut() {
        when(profileService.findVisibleIdByUsername(VIEWER, "target")).thenReturn(Optional.of(TARGET));
        when(relationshipRepository.findAcceptedFollowerIds(TARGET)).thenReturn(List.of(F1, F2));
        // Only F1 hydrates (F2's profile is missing / gone).
        when(profileService.findByIds(List.of(F1, F2))).thenReturn(List.of(
                new ProfileSearchResultDto(F1, "Alice", "alice", "alice.png")));
        when(relationshipRepository.findAcceptedFollowingIdsIn(VIEWER, List.of(F1, F2)))
                .thenReturn(List.of());

        List<FollowProfileSearchResult> result = service.getFollowers(VIEWER.toString(), "target");

        assertThat(result).extracting(FollowProfileSearchResult::id).containsExactly(F1);
    }

    // ---- removeFollower -----------------------------------------------------

    @Test
    void removeFollowerDeletesTheRowWhereTheOtherUserFollowsTheCaller() {
        RelationshipEntity row = acceptedRow(F1, VIEWER);
        when(profileService.findIdByUsername("alice")).thenReturn(Optional.of(F1));
        // Row is (follower=F1, following=VIEWER) — F1 follows the caller.
        when(relationshipRepository.findById(new RelationshipId(F1, VIEWER)))
                .thenReturn(Optional.of(row));

        service.removeFollower(VIEWER.toString(), "alice");

        verify(relationshipRepository).delete(row);
    }

    @Test
    void removeFollowerIsANoOpWhenThatUserIsNotAFollower() {
        when(profileService.findIdByUsername("alice")).thenReturn(Optional.of(F1));
        when(relationshipRepository.findById(new RelationshipId(F1, VIEWER)))
                .thenReturn(Optional.empty());

        service.removeFollower(VIEWER.toString(), "alice");

        verify(relationshipRepository, never()).delete(any());
    }

    @Test
    void removeFollowerWithAnUnknownUsernameThrowsProfileNotFound() {
        when(profileService.findIdByUsername("ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.removeFollower(VIEWER.toString(), "ghost"));

        verifyNoInteractions(relationshipRepository);
    }
}

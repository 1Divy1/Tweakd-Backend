package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.relationships.dto.BlockedAccountDto;
import com.tweakdapp.backend.relationships.exception.CannotBlockSelfException;
import com.tweakdapp.backend.shared.blocking.UserBlockedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure-unit behavior of {@link BlockServiceImpl}: a new block writes the row, removes follows in
 * both directions and publishes {@link UserBlockedEvent}; a repeat block is a silent no-op; the
 * self-block and unknown-username guards; idempotent unblock; and the settings list hydration
 * (block order preserved, vanished profiles skipped).
 */
@ExtendWith(MockitoExtension.class)
class BlockServiceImplTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private BlockedAccountRepository blockedAccountRepository;
    @Mock
    private RelationshipRepository relationshipRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private BlockServiceImpl service;

    private static BlockedAccountEntity row(UUID blocker, UUID blocked, Instant at) {
        BlockedAccountEntity e = new BlockedAccountEntity();
        e.setId(new BlockedId(blocker, blocked));
        e.setCreatedAt(at);
        return e;
    }

    // ---- block --------------------------------------------------------------

    @Test
    void blockSavesTheRowRemovesFollowsBothWaysAndPublishesTheEvent() {
        when(profileService.findIdByUsername("target")).thenReturn(Optional.of(TARGET));
        when(blockedAccountRepository.existsById(new BlockedId(ME, TARGET))).thenReturn(false);

        service.block(ME.toString(), "target");

        ArgumentCaptor<BlockedAccountEntity> saved = ArgumentCaptor.forClass(BlockedAccountEntity.class);
        verify(blockedAccountRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new BlockedId(ME, TARGET));
        verify(relationshipRepository).deleteFollowsBetween(ME, TARGET);
        verify(eventPublisher).publishEvent(new UserBlockedEvent(ME, TARGET));
    }

    @Test
    void blockingAnAlreadyBlockedAccountChangesNothing() {
        when(profileService.findIdByUsername("target")).thenReturn(Optional.of(TARGET));
        when(blockedAccountRepository.existsById(new BlockedId(ME, TARGET))).thenReturn(true);

        service.block(ME.toString(), "target");

        verify(blockedAccountRepository, never()).save(any());
        verifyNoInteractions(relationshipRepository, eventPublisher);
    }

    @Test
    void blockingYourselfIsRejected() {
        when(profileService.findIdByUsername("me")).thenReturn(Optional.of(ME));

        assertThatExceptionOfType(CannotBlockSelfException.class)
                .isThrownBy(() -> service.block(ME.toString(), "me"));
        verifyNoInteractions(blockedAccountRepository, relationshipRepository, eventPublisher);
    }

    @Test
    void blockingAnUnknownUsernameIsNotFound() {
        when(profileService.findIdByUsername("ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.block(ME.toString(), "ghost"));
        verifyNoInteractions(blockedAccountRepository, relationshipRepository, eventPublisher);
    }

    // ---- unblock ------------------------------------------------------------

    @Test
    void unblockDeletesTheCallersBlockRow() {
        BlockedAccountEntity existing = row(ME, TARGET, Instant.now());
        when(profileService.findIdByUsername("target")).thenReturn(Optional.of(TARGET));
        when(blockedAccountRepository.findById(new BlockedId(ME, TARGET))).thenReturn(Optional.of(existing));

        service.unblock(ME.toString(), "target");

        verify(blockedAccountRepository).delete(existing);
    }

    @Test
    void unblockingSomeoneNotBlockedIsANoOp() {
        when(profileService.findIdByUsername("target")).thenReturn(Optional.of(TARGET));
        when(blockedAccountRepository.findById(new BlockedId(ME, TARGET))).thenReturn(Optional.empty());

        service.unblock(ME.toString(), "target");

        verify(blockedAccountRepository, never()).delete(any());
    }

    // ---- listBlocked --------------------------------------------------------

    @Test
    void listBlockedKeepsBlockOrderAndSkipsProfilesThatNoLongerResolve() {
        Instant newer = Instant.parse("2026-09-14T10:00:00Z");
        Instant older = Instant.parse("2026-09-01T10:00:00Z");
        UUID gone = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        when(blockedAccountRepository.findByBlockerNewestFirst(ME))
                .thenReturn(List.of(row(ME, OTHER, newer), row(ME, gone, older), row(ME, TARGET, older)));
        when(profileService.findByIds(List.of(OTHER, gone, TARGET))).thenReturn(List.of(
                new ProfileSearchResultDto(TARGET, "Target", "target", "t.png"),
                new ProfileSearchResultDto(OTHER, null, "other", null)));

        List<BlockedAccountDto> list = service.listBlocked(ME.toString());

        assertThat(list).extracting(BlockedAccountDto::username).containsExactly("other", "target");
        assertThat(list.getFirst().blockedAt()).isEqualTo(newer);
        assertThat(list.getLast().avatarUrl()).isEqualTo("t.png");
    }

    @Test
    void listBlockedIsEmptyWithoutAProfileLookupWhenNobodyIsBlocked() {
        when(blockedAccountRepository.findByBlockerNewestFirst(ME)).thenReturn(List.of());

        assertThat(service.listBlocked(ME.toString())).isEmpty();
        verifyNoInteractions(profileService);
    }
}

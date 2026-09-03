package com.tweakdapp.backend.badges.internal;

import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.BadgeGrantDto;
import com.tweakdapp.backend.badges.dto.BadgeUpsertRequest;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.badges.exception.BadgeAlreadyExistsException;
import com.tweakdapp.backend.badges.exception.BadgeInUseException;
import com.tweakdapp.backend.badges.exception.BadgeNotFoundException;
import com.tweakdapp.backend.badges.internal.entity.BadgeEntity;
import com.tweakdapp.backend.badges.internal.entity.UserBadgeEntity;
import com.tweakdapp.backend.badges.internal.repository.BadgeRepository;
import com.tweakdapp.backend.badges.internal.repository.UserBadgeRepository;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules {@link BadgeServiceImpl} owns, with the database mocked out: what "already earned"
 * means, what a retired badge may and may not be used for, and that the artwork keys the database
 * stores never leave the module unresolved.
 *
 * <p>The idempotency and FK guarantees these tests describe are enforced by the schema too; the
 * pre-checks here are the readable half of them, and the reason a retry gets a 200 rather than a
 * constraint violation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BadgeServiceImplTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String PIONEER = "pioneer";

    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private StorageService storageService;

    private BadgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BadgeServiceImpl(badgeRepository, userBadgeRepository, storageService);
        // @PersistenceContext is field-injected, so a pure unit test has to supply it by hand.
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        // Stand in for the real R2 public-URL construction: prefix, or null for a null key.
        when(storageService.publicUrl(any(StorageBucket.class), any()))
                .thenAnswer(inv -> inv.getArgument(1) == null ? null : "https://assets/" + inv.getArgument(1));
    }

    // ---- fixtures -----------------------------------------------------------

    private static BadgeEntity badge(boolean available) {
        BadgeEntity badge = new BadgeEntity();
        badge.setId(PIONEER);
        badge.setTitle("Pioneer");
        badge.setUnlockedKey("badges/pioneer/badge-unlocked.svg");
        badge.setLockedKey("badges/pioneer/badge-locked.svg");
        badge.setAvailable(available);
        return badge;
    }

    private static UserBadgeEntity held(Instant earnedAt) {
        UserBadgeEntity unlock = new UserBadgeEntity();
        unlock.setId(UUID.randomUUID());
        unlock.setUserId(USER);
        unlock.setBadge(badge(true));
        unlock.setCreatedAt(earnedAt);
        return unlock;
    }

    private void catalogueHas(BadgeEntity badge) {
        when(badgeRepository.existsById(badge.getId())).thenReturn(true);
        when(badgeRepository.findById(badge.getId())).thenReturn(Optional.of(badge));
        when(badgeRepository.findByIdAndAvailableTrue(badge.getId()))
                .thenReturn(badge.isAvailable() ? Optional.of(badge) : Optional.empty());
    }

    // ---- awarding -----------------------------------------------------------

    @Test
    void awardingWritesTheUnlock() {
        catalogueHas(badge(true));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UserBadgeDto result = service.award(USER, PIONEER);

        assertThat(result.badge().id()).isEqualTo(PIONEER);
        verify(userBadgeRepository).saveAndFlush(any());
    }

    /**
     * The reason callers can fire {@code award} from a retried listener with no guard of their own:
     * a second award writes nothing and the unlock date does not move.
     */
    @Test
    void awardingABadgeTheUserAlreadyHoldsIsANoOpThatKeepsTheOriginalDate() {
        Instant firstEarned = Instant.parse("2026-01-01T00:00:00Z");
        catalogueHas(badge(true));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.of(held(firstEarned)));

        UserBadgeDto result = service.award(USER, PIONEER);

        assertThat(result.earnedAt()).isEqualTo(firstEarned);
        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    @Test
    void aRetiredBadgeCanNoLongerBeAwarded() {
        catalogueHas(badge(false));

        assertThatExceptionOfType(BadgeNotFoundException.class)
                .isThrownBy(() -> service.award(USER, PIONEER))
                .withMessageContaining("retired");
        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    @Test
    void anUnknownBadgeCodeIsRejected() {
        when(badgeRepository.findByIdAndAvailableTrue("nope")).thenReturn(Optional.empty());
        when(badgeRepository.existsById("nope")).thenReturn(false);

        assertThatExceptionOfType(BadgeNotFoundException.class)
                .isThrownBy(() -> service.award(USER, "nope"))
                .withMessageContaining("No such badge");
    }

    @Test
    void aHandGrantRecordsTheStaffMemberWhoMadeIt() {
        catalogueHas(badge(true));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grant(USER, PIONEER, STAFF);

        verify(userBadgeRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                unlock -> STAFF.equals(unlock.getGrantedBy())));
    }

    /** An automatic award leaves the granter null — that is what distinguishes it from a hand grant. */
    @Test
    void anAutomaticAwardHasNoGranter() {
        catalogueHas(badge(true));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.award(USER, PIONEER);

        verify(userBadgeRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                unlock -> unlock.getGrantedBy() == null));
    }

    // ---- revoking -----------------------------------------------------------

    @Test
    void revokingDeletesTheUnlock() {
        UserBadgeEntity unlock = held(Instant.now());
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.of(unlock));

        assertThat(service.revoke(USER, PIONEER)).isTrue();
        verify(userBadgeRepository).delete(unlock);
    }

    @Test
    void revokingSomethingTheUserDoesNotHoldIsANoOp() {
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.empty());

        assertThat(service.revoke(USER, PIONEER)).isFalse();
        verify(userBadgeRepository, never()).delete(any());
    }

    /** A badge granted in error must be removable even after the badge itself has been retired. */
    @Test
    void aRetiredBadgeCanStillBeTakenBack() {
        UserBadgeEntity unlock = held(Instant.now());
        unlock.setBadge(badge(false));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.of(unlock));

        assertThat(service.revoke(USER, PIONEER)).isTrue();
    }

    // ---- artwork URLs -------------------------------------------------------

    /** The client never sees an R2 key; the module resolves both variants on the way out. */
    @Test
    void bothArtworkKeysAreResolvedToPublicUrls() {
        when(badgeRepository.findByAvailableTrueOrderByCreatedAtAsc()).thenReturn(List.of(badge(true)));

        BadgeDto dto = service.listCatalogue().getFirst();

        assertThat(dto.unlockedUrl()).isEqualTo("https://assets/badges/pioneer/badge-unlocked.svg");
        assertThat(dto.lockedUrl()).isEqualTo("https://assets/badges/pioneer/badge-locked.svg");
        verify(storageService).publicUrl(StorageBucket.ASSETS, "badges/pioneer/badge-unlocked.svg");
    }

    /** No locked artwork means an explicit null, not a URL pointing at nothing. */
    @Test
    void aBadgeWithNoLockedVariantResolvesToANullLockedUrl() {
        BadgeEntity noLocked = badge(true);
        noLocked.setLockedKey(null);
        when(badgeRepository.findByAvailableTrueOrderByCreatedAtAsc()).thenReturn(List.of(noLocked));

        assertThat(service.listCatalogue().getFirst().lockedUrl()).isNull();
    }

    // ---- the locked section -------------------------------------------------

    /** The locked list is an anti-join in the database, not a subtraction done here. */
    @Test
    void lockedBadgesComeFromTheAntiJoin() {
        when(badgeRepository.findLockedForUser(USER)).thenReturn(List.of(badge(true)));

        List<BadgeDto> locked = service.listLockedBadges(USER);

        assertThat(locked).extracting(BadgeDto::id).containsExactly(PIONEER);
        assertThat(locked.getFirst().lockedUrl()).isEqualTo("https://assets/badges/pioneer/badge-locked.svg");
    }

    // ---- the granter is staff-only ------------------------------------------

    /**
     * The app-facing shape has no granter field at all — the only way to expose one is the
     * dashboard read, so a hand-grant cannot leak onto a profile as "granted by X".
     */
    @Test
    void theGranterIsVisibleOnlyThroughTheAdminRead() {
        UserBadgeEntity handGranted = held(Instant.now());
        handGranted.setGrantedBy(STAFF);
        when(userBadgeRepository.findAllForUser(USER)).thenReturn(List.of(handGranted));

        assertThat(service.listGrantsForAdmin(USER))
                .extracting(BadgeGrantDto::grantedBy).containsExactly(STAFF);

        // Same row, app-facing shape: the record has no component that could carry the granter.
        UserBadgeDto appFacing = service.listUserBadges(USER).getFirst();
        assertThat(UserBadgeDto.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("badge", "earnedAt");
        assertThat(appFacing.badge().id()).isEqualTo(PIONEER);
    }

    // ---- administration -----------------------------------------------------

    @Test
    void creatingABadgeWhoseCodeIsTakenIsAConflict() {
        when(badgeRepository.existsById(PIONEER)).thenReturn(true);

        assertThatExceptionOfType(BadgeAlreadyExistsException.class)
                .isThrownBy(() -> service.create(PIONEER, upsert()));
    }

    /** Codes are typed into a form but referenced from Java constants — so they are canonicalised. */
    @Test
    void aNewBadgeCodeIsNormalised() {
        when(badgeRepository.existsById(PIONEER)).thenReturn(false);
        when(badgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create("  Pioneer ", upsert()).id()).isEqualTo(PIONEER);
    }

    @Test
    void aBadgeUsersHoldCannotBeDeleted() {
        catalogueHas(badge(true));
        when(userBadgeRepository.countByBadgeId(PIONEER)).thenReturn(3L);

        assertThatExceptionOfType(BadgeInUseException.class)
                .isThrownBy(() -> service.delete(PIONEER))
                .withMessageContaining("Retire it instead");
        verify(badgeRepository, never()).delete(any());
    }

    @Test
    void aBadgeNobodyHoldsCanBeDeleted() {
        catalogueHas(badge(true));
        when(userBadgeRepository.countByBadgeId(PIONEER)).thenReturn(0L);

        service.delete(PIONEER);

        verify(badgeRepository).delete(any());
    }

    /** Badges nobody holds still get a row, so the dashboard renders the whole catalogue. */
    @Test
    void holderCountsCoverEveryBadgeIncludingTheUnearnedOnes() {
        BadgeEntity other = badge(true);
        other.setId("veteran");
        when(badgeRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(badge(true), other));
        when(badgeRepository.countHoldersByBadge())
                .thenReturn(List.<Object[]>of(new Object[]{PIONEER, 7L}));

        assertThat(service.holderCounts()).containsEntry(PIONEER, 7L).containsEntry("veteran", 0L);
    }

    private static BadgeUpsertRequest upsert() {
        return new BadgeUpsertRequest("Pioneer", "Early member",
                "badges/pioneer/badge-unlocked.svg", "badges/pioneer/badge-locked.svg", true);
    }
}

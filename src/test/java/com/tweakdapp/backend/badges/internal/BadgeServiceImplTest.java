package com.tweakdapp.backend.badges.internal;

import com.tweakdapp.backend.badges.BadgeTrigger;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.BadgeGrantDto;
import com.tweakdapp.backend.badges.dto.BadgeUpsertRequest;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.badges.exception.BadgeAlreadyExistsException;
import com.tweakdapp.backend.badges.exception.BadgeInUseException;
import com.tweakdapp.backend.badges.exception.BadgeNotFoundException;
import com.tweakdapp.backend.badges.exception.InvalidBadgeDefinitionException;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
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

    /** Inside `pioneer`'s configured window — the moment an account was created, not "now". */
    private static final Instant SIGNED_UP_AT = Instant.parse("2026-09-06T12:00:00Z");

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

    /** The badge as `pioneer` is actually configured: signup-triggered, offered for one year. */
    private static BadgeEntity windowed(Instant from, Instant until) {
        BadgeEntity badge = badge(true);
        badge.setAwardTrigger(BadgeTrigger.ACCOUNT_CREATED.code());
        badge.setEarnableFrom(from);
        badge.setEarnableUntil(until);
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
        when(badgeRepository.findEarnableAt(any())).thenReturn(List.of(badge(true)));

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
        when(badgeRepository.findEarnableAt(any())).thenReturn(List.of(noLocked));

        assertThat(service.listCatalogue().getFirst().lockedUrl()).isNull();
    }

    // ---- the locked section -------------------------------------------------

    /** The locked list is an anti-join in the database, not a subtraction done here. */
    @Test
    void lockedBadgesComeFromTheAntiJoin() {
        when(badgeRepository.findLockedForUser(eq(USER), any())).thenReturn(List.of(badge(true)));

        List<BadgeDto> locked = service.listLockedBadges(USER);

        assertThat(locked).extracting(BadgeDto::id).containsExactly(PIONEER);
        assertThat(locked.getFirst().lockedUrl()).isEqualTo("https://assets/badges/pioneer/badge-locked.svg");
    }

    // ---- the unlock-animation queue --------------------------------------------

    /** The pending-celebration read resolves artwork the same way every other badge read does. */
    @Test
    void pendingCelebrationsCarryResolvedArtwork() {
        when(userBadgeRepository.findPendingCelebrationForUser(USER)).thenReturn(List.of(held(Instant.now())));

        List<UserBadgeDto> pending = service.listPendingCelebrations(USER);

        assertThat(pending).extracting(dto -> dto.badge().id()).containsExactly(PIONEER);
        assertThat(pending.getFirst().badge().unlockedUrl())
                .isEqualTo("https://assets/badges/pioneer/badge-unlocked.svg");
    }

    /** The first acknowledgement flips a row, so the service reports it as the one that counted. */
    @Test
    void acknowledgingACelebrationThatFlipsARowReturnsTrue() {
        when(userBadgeRepository.markCelebrated(USER, PIONEER)).thenReturn(1);

        assertThat(service.markCelebrated(USER, PIONEER)).isTrue();
    }

    /**
     * A repeat acknowledgement, or one for a badge the user does not hold, matches no row — the
     * {@code granted_in_app = false} predicate in the update is the idempotency, not a pre-check.
     */
    @Test
    void acknowledgingACelebrationThatChangesNothingReturnsFalse() {
        when(userBadgeRepository.markCelebrated(USER, PIONEER)).thenReturn(0);

        assertThat(service.markCelebrated(USER, PIONEER)).isFalse();
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

    // ---- awarding for an event ----------------------------------------------

    /**
     * The point of the trigger SPI: the caller reports what happened and the catalogue decides what
     * it is worth, so a second badge on an existing event needs no deploy — here, two of them.
     */
    @Test
    void awardingForATriggerUnlocksEveryBadgeTheEventIsCurrentlyWorth() {
        BadgeEntity veteran = windowed(null, null);
        veteran.setId("founder");
        when(badgeRepository.findUnheldForTrigger(eq("account_created"), any(), eq(USER)))
                .thenReturn(List.of(windowed(null, null), veteran));
        when(userBadgeRepository.findForUserAndBadge(eq(USER), any())).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        List<UserBadgeDto> unlocked =
                service.awardForTrigger(USER, BadgeTrigger.ACCOUNT_CREATED, SIGNED_UP_AT);

        assertThat(unlocked).extracting(ub -> ub.badge().id()).containsExactly(PIONEER, "founder");
    }

    /**
     * The usual case on any repeat — a re-run onboarding, a retried listener. The query has already
     * excluded what the user holds, so there is nothing left to write.
     */
    @Test
    void awardingForATriggerThatMatchesNothingWritesNothing() {
        when(badgeRepository.findUnheldForTrigger(any(), any(), any())).thenReturn(List.of());

        assertThat(service.awardForTrigger(USER, BadgeTrigger.ACCOUNT_CREATED, SIGNED_UP_AT)).isEmpty();
        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    /**
     * The whole reason the moment is a parameter: a limited-time badge is judged against when the
     * user signed up, not when this call happens. Onboarding a week after the cutoff must still
     * pay out a badge earned by signing up before it.
     */
    @Test
    void awardingForATriggerJudgesTheWindowAtTheMomentGivenRatherThanNow() {
        when(badgeRepository.findUnheldForTrigger(any(), any(), any())).thenReturn(List.of());

        service.awardForTrigger(USER, BadgeTrigger.ACCOUNT_CREATED, SIGNED_UP_AT);

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(badgeRepository).findUnheldForTrigger(eq("account_created"), at.capture(), eq(USER));
        assertThat(at.getValue()).isEqualTo(SIGNED_UP_AT);
    }

    /** A rule awarded it, not a person — so there is no staff member to blame or thank. */
    @Test
    void aBadgeAwardedByARuleRecordsNoGranter() {
        when(badgeRepository.findUnheldForTrigger(any(), any(), any()))
                .thenReturn(List.of(windowed(null, null)));
        when(userBadgeRepository.findForUserAndBadge(eq(USER), any())).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.awardForTrigger(USER, BadgeTrigger.ACCOUNT_CREATED, SIGNED_UP_AT);

        ArgumentCaptor<UserBadgeEntity> saved = ArgumentCaptor.forClass(UserBadgeEntity.class);
        verify(userBadgeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGrantedBy()).isNull();
    }

    /** A caller that cannot say what happened, or when, would award the wrong badges silently. */
    @Test
    void awardingForATriggerNeedsBothTheEventAndItsMoment() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.awardForTrigger(USER, null, SIGNED_UP_AT));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.awardForTrigger(USER, BadgeTrigger.ACCOUNT_CREATED, null));
        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    // ---- the offer window ---------------------------------------------------

    /**
     * What makes `pioneer` limited-time without anybody remembering a date: once the window closes,
     * the automatic path refuses it. Its own message, because this refusal is by design and must
     * not read as a missing badge.
     */
    @Test
    void awardingRefusesABadgeWhoseOfferWindowHasClosed() {
        catalogueHas(windowed(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2021-01-01T00:00:00Z")));

        assertThatExceptionOfType(BadgeNotFoundException.class)
                .isThrownBy(() -> service.award(USER, PIONEER))
                .withMessageContaining("outside its earnable window");
        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    /** The other half of the same column pair: a badge staged to open later is not awardable yet. */
    @Test
    void awardingRefusesABadgeWhoseOfferWindowHasNotOpenedYet() {
        catalogueHas(windowed(Instant.parse("2099-01-01T00:00:00Z"), null));

        assertThatExceptionOfType(BadgeNotFoundException.class)
                .isThrownBy(() -> service.award(USER, PIONEER))
                .withMessageContaining("outside its earnable window");
    }

    /** An unbounded badge is the ordinary case and answers on availability alone. */
    @Test
    void aBadgeWithNoWindowIsAlwaysAwardable() {
        catalogueHas(windowed(null, null));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.award(USER, PIONEER).badge().id()).isEqualTo(PIONEER);
    }

    /**
     * The deliberate asymmetry: a window bounds the automatic offer, not what staff may do. Support
     * has to be able to hand `pioneer` to someone who qualified and lost the account — and that
     * path is the one that records who did it.
     */
    @Test
    void staffCanStillGrantABadgeWhoseOfferWindowHasClosed() {
        catalogueHas(windowed(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2021-01-01T00:00:00Z")));
        when(userBadgeRepository.findForUserAndBadge(USER, PIONEER)).thenReturn(Optional.empty());
        when(userBadgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grant(USER, PIONEER, STAFF);

        ArgumentCaptor<UserBadgeEntity> saved = ArgumentCaptor.forClass(UserBadgeEntity.class);
        verify(userBadgeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGrantedBy()).isEqualTo(STAFF);
    }

    /** Retiring is still absolute — it is the switch that stops a badge being handed out at all. */
    @Test
    void staffCannotGrantARetiredBadge() {
        catalogueHas(badge(false));

        assertThatExceptionOfType(BadgeNotFoundException.class)
                .isThrownBy(() -> service.grant(USER, PIONEER, STAFF))
                .withMessageContaining("retired");
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

    /**
     * A trigger the backend never fires would leave a badge that looks live on the dashboard and
     * silently never unlocks — the kind of failure nobody notices for weeks. Refused at the edge,
     * and by the column's CHECK constraint for anything that arrives another way.
     */
    @Test
    void aBadgeCannotBeSavedWithATriggerTheBackendDoesNotFire() {
        when(badgeRepository.existsById(PIONEER)).thenReturn(false);

        assertThatExceptionOfType(InvalidBadgeDefinitionException.class)
                .isThrownBy(() -> service.create(PIONEER, upsert("first_haircut", null, null)))
                .withMessageContaining("account_created");
        verify(badgeRepository, never()).saveAndFlush(any());
    }

    /** Blank means "no trigger; staff hand this one out" — the normal case, not an error. */
    @Test
    void aBadgeWithNoTriggerIsHandGrantedAndThatIsFine() {
        when(badgeRepository.existsById(PIONEER)).thenReturn(false);
        when(badgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(PIONEER, upsert("   ", null, null)).awardTrigger()).isNull();
    }

    /** Typed into a form, compared against a stored string — so it is canonicalised on the way in. */
    @Test
    void aTriggerCodeIsNormalisedOnSave() {
        when(badgeRepository.existsById(PIONEER)).thenReturn(false);
        when(badgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(PIONEER, upsert(" ACCOUNT_CREATED ", null, null)).awardTrigger())
                .isEqualTo("account_created");
    }

    /** A window that ends before it opens is a typo that presents as a badge nobody can earn. */
    @Test
    void aBadgeCannotBeSavedWithAWindowThatEndsBeforeItOpens() {
        catalogueHas(badge(true));

        assertThatExceptionOfType(InvalidBadgeDefinitionException.class)
                .isThrownBy(() -> service.update(PIONEER, upsert(
                        "account_created",
                        Instant.parse("2027-09-06T00:00:00Z"),
                        Instant.parse("2026-09-06T00:00:00Z"))));
    }

    /** The window round-trips to the client, which is what lets the app show "X days left". */
    @Test
    void theOfferWindowIsCarriedOnTheBadgeTheDashboardReadsBack() {
        when(badgeRepository.existsById(PIONEER)).thenReturn(false);
        when(badgeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BadgeDto saved = service.create(PIONEER, upsert(
                "account_created",
                Instant.parse("2026-09-06T00:00:00Z"),
                Instant.parse("2027-09-06T00:00:00Z")));

        assertThat(saved.earnableFrom()).isEqualTo(Instant.parse("2026-09-06T00:00:00Z"));
        assertThat(saved.earnableUntil()).isEqualTo(Instant.parse("2027-09-06T00:00:00Z"));
    }

    private static BadgeUpsertRequest upsert(String trigger, Instant from, Instant until) {
        return new BadgeUpsertRequest("Pioneer", "Early member",
                "badges/pioneer/badge-unlocked.svg", "badges/pioneer/badge-locked.svg", true,
                trigger, from, until);
    }

    private static BadgeUpsertRequest upsert() {
        return new BadgeUpsertRequest("Pioneer", "Early member",
                "badges/pioneer/badge-unlocked.svg", "badges/pioneer/badge-locked.svg", true,
                null, null, null);
    }
}

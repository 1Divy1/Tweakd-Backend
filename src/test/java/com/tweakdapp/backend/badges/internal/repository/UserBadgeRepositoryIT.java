package com.tweakdapp.backend.badges.internal.repository;

import com.tweakdapp.backend.badges.internal.entity.UserBadgeEntity;
import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The guarantees the badges schema itself makes, against the real Supabase shape in a PostGIS
 * container. These are the ones the service leans on rather than re-implements:
 *
 * <ul>
 *   <li>a user cannot hold the same badge twice — the unique index, which is what makes
 *       {@code award} idempotent under concurrency rather than only under a well-timed pre-check</li>
 *   <li>a badge somebody earned cannot be deleted — {@code ON DELETE RESTRICT}, which is why
 *       retiring exists at all</li>
 *   <li>a retired badge stays readable on the profiles that hold it</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserBadgeRepositoryIT extends AbstractPostgresIT {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER_USER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    /**
     * The moment every awardability question below is asked about. A fixed instant rather than
     * {@code Instant.now()} so the window tests state their boundaries relative to something the
     * test controls — the production callers pass the moment the achievement happened, which is
     * exactly this parameter.
     */
    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

    @Autowired
    private UserBadgeRepository userBadgeRepository;
    @Autowired
    private BadgeRepository badgeRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.user_badges");
        jdbc.update("delete from public.badges");
        createProfile(USER, "racer_b1");
        createProfile(OTHER_USER, "racer_b2");
    }

    // ---- fixture helpers ----------------------------------------------------

    private void createProfile(UUID id, String username) {
        jdbc.update("insert into auth.users (id) values (?) on conflict do nothing", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?) on conflict do nothing", id, username);
    }

    private void createBadge(String id, boolean available) {
        jdbc.update("""
                insert into public.badges (id, title, unlocked_badge_url, locked_badge_url, is_available)
                values (?, ?, ?, ?, ?)
                """, id, id, "badges/" + id + "/badge-unlocked.svg", "badges/" + id + "/badge-locked.svg", available);
    }

    /** A badge configured the way `pioneer` is: bound to an event, offered for a bounded window. */
    private void createTriggeredBadge(String id, String trigger, Instant from, Instant until) {
        createBadge(id, true);
        jdbc.update("update public.badges set award_trigger = ?, earnable_from = ?, earnable_until = ? where id = ?",
                trigger,
                from == null ? null : java.sql.Timestamp.from(from),
                until == null ? null : java.sql.Timestamp.from(until),
                id);
    }

    private void award(UUID userId, String badgeId) {
        jdbc.update("insert into public.user_badges (id, user_id, badge_id) values (?, ?, ?)",
                UUID.randomUUID(), userId, badgeId);
    }

    // ---- idempotency --------------------------------------------------------

    /**
     * The guarantee behind {@code BadgeService.award} needing no caller-side dedup: two inserts of
     * the same pair cannot both land, however they are interleaved.
     */
    @Test
    void aUserCannotHoldTheSameBadgeTwice() {
        createBadge("pioneer", true);
        award(USER, "pioneer");

        assertThatExceptionOfType(DuplicateKeyException.class)
                .isThrownBy(() -> award(USER, "pioneer"));
    }

    @Test
    void twoUsersCanHoldTheSameBadge() {
        createBadge("pioneer", true);
        award(USER, "pioneer");
        award(OTHER_USER, "pioneer");

        assertThat(userBadgeRepository.countByBadgeId("pioneer")).isEqualTo(2);
    }

    // ---- the catalogue foreign key ------------------------------------------

    /** Why withdrawing a badge is a flag and not a delete: the database refuses the delete. */
    @Test
    void aBadgeSomebodyEarnedCannotBeDeleted() {
        createBadge("pioneer", true);
        award(USER, "pioneer");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("delete from public.badges where id = 'pioneer'"));
    }

    /** Retiring is the supported withdrawal, and it leaves the holders alone. */
    @Test
    void aRetiredBadgeStaysOnTheProfilesThatEarnedIt() {
        createBadge("pioneer", true);
        award(USER, "pioneer");

        jdbc.update("update public.badges set is_available = false where id = 'pioneer'");

        List<UserBadgeEntity> held = userBadgeRepository.findAllForUser(USER);
        assertThat(held).hasSize(1);
        assertThat(held.getFirst().getBadge().isAvailable()).isFalse();
    }

    // ---- reads --------------------------------------------------------------

    @Test
    void aUsersBadgesComeBackNewestUnlockFirst() {
        createBadge("pioneer", true);
        createBadge("veteran", true);
        award(USER, "pioneer");
        jdbc.update("update public.user_badges set created_at = now() - interval '1 day' where badge_id = 'pioneer'");
        award(USER, "veteran");

        assertThat(userBadgeRepository.findAllForUser(USER))
                .extracting(ub -> ub.getBadge().getId())
                .containsExactly("veteran", "pioneer");
    }

    @Test
    void aUsersBadgesAreScopedToThatUser() {
        createBadge("pioneer", true);
        award(OTHER_USER, "pioneer");

        assertThat(userBadgeRepository.findAllForUser(USER)).isEmpty();
        assertThat(userBadgeRepository.existsByUserIdAndBadgeId(USER, "pioneer")).isFalse();
        assertThat(userBadgeRepository.existsByUserIdAndBadgeId(OTHER_USER, "pioneer")).isTrue();
    }

    // ---- the unlock-animation queue --------------------------------------------

    /** A fresh unlock owes an animation — the column default puts it on the pending list. */
    @Test
    void aFreshUnlockIsPendingCelebration() {
        createBadge("pioneer", true);
        award(USER, "pioneer");

        assertThat(userBadgeRepository.findPendingCelebrationForUser(USER))
                .extracting(ub -> ub.getBadge().getId()).containsExactly("pioneer");
    }

    /** Oldest unlock first, so a client several badges behind animates them in earned order. */
    @Test
    void pendingCelebrationsComeBackOldestUnlockFirst() {
        createBadge("pioneer", true);
        createBadge("veteran", true);
        award(USER, "pioneer");
        jdbc.update("update public.user_badges set created_at = now() - interval '1 day' where badge_id = 'pioneer'");
        award(USER, "veteran");

        assertThat(userBadgeRepository.findPendingCelebrationForUser(USER))
                .extracting(ub -> ub.getBadge().getId()).containsExactly("pioneer", "veteran");
    }

    /** The acknowledgement flips exactly one row and takes it off the pending list. */
    @Test
    void markCelebratedFlipsTheRowAndRemovesItFromPending() {
        createBadge("pioneer", true);
        createBadge("veteran", true);
        award(USER, "pioneer");
        award(USER, "veteran");

        assertThat(userBadgeRepository.markCelebrated(USER, "pioneer")).isEqualTo(1);

        assertThat(userBadgeRepository.findPendingCelebrationForUser(USER))
                .extracting(ub -> ub.getBadge().getId()).containsExactly("veteran");
        assertThat(jdbc.queryForObject(
                "select granted_in_app from public.user_badges where user_id = ? and badge_id = 'pioneer'",
                Boolean.class, USER)).isTrue();
    }

    /** Idempotent: a second acknowledgement, or one for a badge not held, matches no row. */
    @Test
    void markCelebratedIsANoOpTheSecondTimeAndForBadgesNotHeld() {
        createBadge("pioneer", true);
        award(USER, "pioneer");
        userBadgeRepository.markCelebrated(USER, "pioneer");

        assertThat(userBadgeRepository.markCelebrated(USER, "pioneer")).isZero();
        assertThat(userBadgeRepository.markCelebrated(USER, "veteran")).isZero();
        assertThat(userBadgeRepository.markCelebrated(OTHER_USER, "pioneer")).isZero();
    }

    /** One user acknowledging their animation does not touch another user's pending badge. */
    @Test
    void markCelebratedIsScopedToTheUser() {
        createBadge("pioneer", true);
        award(USER, "pioneer");
        award(OTHER_USER, "pioneer");

        userBadgeRepository.markCelebrated(USER, "pioneer");

        assertThat(userBadgeRepository.findPendingCelebrationForUser(OTHER_USER))
                .extracting(ub -> ub.getBadge().getId()).containsExactly("pioneer");
    }

    // ---- the locked list ----------------------------------------------------

    /** The anti-join behind the locked section: available badges minus the ones they hold. */
    @Test
    void lockedBadgesAreTheAwardableOnesTheUserDoesNotHold() {
        createBadge("pioneer", true);
        createBadge("veteran", true);
        award(USER, "pioneer");

        assertThat(badgeRepository.findLockedForUser(USER, NOW))
                .extracting(b -> b.getId()).containsExactly("veteran");
    }

    /** Something that can no longer be earned is not a goal, so retired badges are not "locked". */
    @Test
    void retiredBadgesAreNotOfferedAsLocked() {
        createBadge("pioneer", false);

        assertThat(badgeRepository.findLockedForUser(USER, NOW)).isEmpty();
    }

    /**
     * The asymmetry that follows: a badge retired after someone earned it stays on their profile
     * while being absent from both the catalogue and everyone else's locked list.
     */
    @Test
    void aBadgeRetiredAfterTheFactStaysEarnedButStopsBeingLockableForOthers() {
        createBadge("pioneer", true);
        award(USER, "pioneer");
        jdbc.update("update public.badges set is_available = false where id = 'pioneer'");

        assertThat(userBadgeRepository.findAllForUser(USER)).hasSize(1);
        assertThat(badgeRepository.findLockedForUser(OTHER_USER, NOW)).isEmpty();
        assertThat(badgeRepository.findEarnableAt(NOW)).isEmpty();
    }

    /** The locked list is per user, not a global "unearned" set. */
    @Test
    void lockedIsScopedToTheUserAsking() {
        createBadge("pioneer", true);
        award(USER, "pioneer");

        assertThat(badgeRepository.findLockedForUser(USER, NOW)).isEmpty();
        assertThat(badgeRepository.findLockedForUser(OTHER_USER, NOW))
                .extracting(b -> b.getId()).containsExactly("pioneer");
    }

    // ---- the trigger query --------------------------------------------------

    /**
     * The single query behind {@code awardForTrigger}: bound to this event, inside its window, not
     * already held. A badge on a different trigger is another event's business.
     */
    @Test
    void theTriggerQueryReturnsOnlyBadgesBoundToThatEvent() {
        createTriggeredBadge("pioneer", "account_created", null, null);
        createBadge("veteran", true);   // hand-granted: no trigger at all

        assertThat(badgeRepository.findUnheldForTrigger("account_created", NOW, USER))
                .extracting(b -> b.getId()).containsExactly("pioneer");
    }

    /** The anti-join that makes a repeated onboarding write nothing at all. */
    @Test
    void theTriggerQueryExcludesBadgesTheUserAlreadyHolds() {
        createTriggeredBadge("pioneer", "account_created", null, null);
        award(USER, "pioneer");

        assertThat(badgeRepository.findUnheldForTrigger("account_created", NOW, USER)).isEmpty();
        assertThat(badgeRepository.findUnheldForTrigger("account_created", NOW, OTHER_USER))
                .extracting(b -> b.getId()).containsExactly("pioneer");
    }

    /**
     * The window is half-open, [from, until). The boundary matters in exactly one place — the
     * instant a limited-time badge expires — and both ends are checked here so an off-by-one
     * cannot hand out a badge for a year longer than intended, or a day less.
     */
    @Test
    void theTriggerWindowIncludesItsStartAndExcludesItsEnd() {
        Instant from = Instant.parse("2026-09-06T00:00:00Z");
        Instant until = Instant.parse("2027-09-06T00:00:00Z");
        createTriggeredBadge("pioneer", "account_created", from, until);

        assertThat(badgeRepository.findUnheldForTrigger("account_created", from, USER))
                .as("an account created at the instant the offer opens qualifies")
                .hasSize(1);
        assertThat(badgeRepository.findUnheldForTrigger("account_created", until.minusMillis(1), USER))
                .as("the last instant inside the window still qualifies")
                .hasSize(1);
        assertThat(badgeRepository.findUnheldForTrigger("account_created", until, USER))
                .as("the instant the offer closes does not")
                .isEmpty();
        assertThat(badgeRepository.findUnheldForTrigger("account_created", from.minusMillis(1), USER))
                .as("nor does anything before it opened")
                .isEmpty();
    }

    /** Retiring stops the automatic path too — it is the switch that overrides everything. */
    @Test
    void theTriggerQueryExcludesRetiredBadges() {
        createTriggeredBadge("pioneer", "account_created", null, null);
        jdbc.update("update public.badges set is_available = false where id = 'pioneer'");

        assertThat(badgeRepository.findUnheldForTrigger("account_created", NOW, USER)).isEmpty();
    }

    /**
     * What the app sees once a limited-time badge expires: it leaves the catalogue and the locked
     * list on its own, so nothing advertises a badge that can no longer be handed out. The users
     * already holding it keep it — the same asymmetry retiring has.
     */
    @Test
    void anExpiredBadgeLeavesTheCatalogueAndTheLockedListButNotItsHolders() {
        createTriggeredBadge("pioneer", "account_created", null, Instant.parse("2026-09-06T00:00:00Z"));
        award(USER, "pioneer");

        assertThat(badgeRepository.findEarnableAt(NOW)).isEmpty();
        assertThat(badgeRepository.findLockedForUser(OTHER_USER, NOW)).isEmpty();
        assertThat(userBadgeRepository.findAllForUser(USER)).hasSize(1);
    }

    /** A badge staged to open later is not offered yet, on either read. */
    @Test
    void aBadgeStagedToOpenLaterIsNotOfferedYet() {
        createTriggeredBadge("pioneer", "account_created", Instant.parse("2099-01-01T00:00:00Z"), null);

        assertThat(badgeRepository.findEarnableAt(NOW)).isEmpty();
        assertThat(badgeRepository.findLockedForUser(USER, NOW)).isEmpty();
        assertThat(badgeRepository.findUnheldForTrigger("account_created", NOW, USER)).isEmpty();
    }

    // ---- the CHECK constraints ----------------------------------------------

    /**
     * A trigger the backend never fires would leave a badge that silently never unlocks. The
     * service refuses it at the edge; this is the guarantee that holds for anything written
     * straight to the database.
     */
    @Test
    void aTriggerCodeTheBackendDoesNotKnowIsRefused() {
        createBadge("pioneer", true);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update(
                        "update public.badges set award_trigger = 'first_haircut' where id = 'pioneer'"));
    }

    /** A window that closes before it opens can never award anything — a typo, not a configuration. */
    @Test
    void aWindowThatEndsBeforeItOpensIsRefused() {
        createBadge("pioneer", true);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("""
                        update public.badges
                           set earnable_from  = timestamptz '2027-09-06 00:00:00+00',
                               earnable_until = timestamptz '2026-09-06 00:00:00+00'
                         where id = 'pioneer'
                        """));
    }

    /** The CHECK that keeps the columns keys: a full URL would silently double the public prefix. */
    @Test
    void aFullUrlIsRefusedWhereAnObjectKeyBelongs() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("""
                        insert into public.badges (id, title, unlocked_badge_url)
                        values ('bad', 'Bad', 'https://assets.example/badges/bad.svg')
                        """));
    }
}

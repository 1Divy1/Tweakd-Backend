package com.tweakdapp.backend.presence.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory presence state machine: session sets, the online transition signal, and the
 * offline grace window (removing a user's last session queues a "pending offline" that only the
 * sweep finalizes, and a reconnect inside the window cancels it). Grace timing is driven purely by
 * the {@code Duration} passed to {@link PresenceRegistry#sweepExpired}, so a negative duration
 * forces a future cutoff (everything pending expires) and a large positive one forces a past cutoff
 * (nothing expires) — fully deterministic without touching the clock.
 */
class PresenceRegistryTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** cutoff = now - (-1h) = now + 1h → every pending stamp is before it → everything expires. */
    private static final Duration FORCE_EXPIRY = Duration.ofHours(-1);
    /** cutoff = now - 1h → no pending stamp (≈ now) is before it → nothing expires. */
    private static final Duration WITHIN_GRACE = Duration.ofHours(1);

    private final PresenceRegistry registry = new PresenceRegistry();

    @Test
    void firstSessionTransitionsUserOnline() {
        assertThat(registry.addSession(USER, "s1")).isTrue();
    }

    @Test
    void secondConcurrentSessionDoesNotRetransitionOnline() {
        registry.addSession(USER, "s1");
        assertThat(registry.addSession(USER, "s2")).isFalse();
    }

    @Test
    void duplicateSessionIdDoesNotRetransitionOnline() {
        registry.addSession(USER, "s1");
        assertThat(registry.addSession(USER, "s1")).isFalse();
    }

    @Test
    void reconnectWithinGraceCancelsPendingOfflineWithoutAnOnlineTransition() {
        registry.addSession(USER, "s1");
        registry.removeSession(USER, "s1"); // last session gone → pending offline

        assertThat(registry.addSession(USER, "s2")).isFalse(); // reconnect: publicly nothing happened
        assertThat(registry.sweepExpired(FORCE_EXPIRY)).isEmpty(); // the pending was cancelled
    }

    @Test
    void isOnlineTrueWhileASessionIsOpen() {
        registry.addSession(USER, "s1");
        assertThat(registry.isOnline(USER)).isTrue();
    }

    @Test
    void isOnlineTrueDuringTheGraceWindow() {
        registry.addSession(USER, "s1");
        registry.removeSession(USER, "s1");
        assertThat(registry.isOnline(USER)).isTrue();
    }

    @Test
    void isOnlineFalseForAnUnknownUser() {
        assertThat(registry.isOnline(USER)).isFalse();
    }

    @Test
    void removingOneOfSeveralSessionsKeepsTheUserOnlineWithoutEnteringGrace() {
        registry.addSession(USER, "s1");
        registry.addSession(USER, "s2");

        registry.removeSession(USER, "s1");

        assertThat(registry.isOnline(USER)).isTrue();
        assertThat(registry.sweepExpired(FORCE_EXPIRY)).isEmpty(); // still has s2, never pending
    }

    @Test
    void removingTheLastSessionThenSweepingFinalizesOffline() {
        registry.addSession(USER, "s1");
        registry.removeSession(USER, "s1");

        assertThat(registry.sweepExpired(FORCE_EXPIRY)).containsExactly(USER);
        assertThat(registry.isOnline(USER)).isFalse();
    }

    @Test
    void sweepDoesNotFinalizeBeforeTheGraceElapses() {
        registry.addSession(USER, "s1");
        registry.removeSession(USER, "s1");

        assertThat(registry.sweepExpired(WITHIN_GRACE)).isEmpty();
        assertThat(registry.isOnline(USER)).isTrue(); // still riding out the window
    }

    @Test
    void removingAnUnknownSessionIsANoOp() {
        registry.removeSession(USER, "ghost");

        assertThat(registry.isOnline(USER)).isFalse();
        assertThat(registry.sweepExpired(FORCE_EXPIRY)).isEmpty(); // no pending was created
    }

    @Test
    void aDoubleDisconnectOfTheSameSessionDoesNotDoubleEnterGrace() {
        registry.addSession(USER, "s1");
        registry.removeSession(USER, "s1");
        registry.removeSession(USER, "s1"); // duplicate disconnect event is harmless

        assertThat(registry.sweepExpired(FORCE_EXPIRY)).containsExactly(USER);
    }

    @Test
    void sweepReturnsEachExpiredUserOnlyOnce() {
        registry.addSession(USER, "s1");
        registry.removeSession(USER, "s1");

        assertThat(registry.sweepExpired(FORCE_EXPIRY)).containsExactly(USER);
        assertThat(registry.sweepExpired(FORCE_EXPIRY)).isEmpty(); // already finalized
    }

    @Test
    void onlineUsersSnapshotExcludesGraceWindowUsers() {
        registry.addSession(USER, "s1");
        registry.addSession(OTHER, "s2");
        registry.removeSession(OTHER, "s2"); // OTHER is pending, not truly online

        assertThat(registry.onlineUsers()).containsExactly(USER);
    }

    @Test
    void onlineUsersSnapshotIsADefensiveCopy() {
        registry.addSession(USER, "s1");

        Set<UUID> snapshot = registry.onlineUsers();
        snapshot.clear();

        assertThat(registry.onlineUsers()).containsExactly(USER);
    }

    @Test
    void sweepReturnsOnlyExpiredUsersLeavingLiveOnesAlone() {
        registry.addSession(USER, "s1"); // stays online
        registry.addSession(OTHER, "s2");
        registry.removeSession(OTHER, "s2"); // pending → will expire

        List<UUID> expired = registry.sweepExpired(FORCE_EXPIRY);

        assertThat(expired).containsExactly(OTHER);
        assertThat(registry.isOnline(USER)).isTrue();
    }
}

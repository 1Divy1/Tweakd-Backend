package com.carsocialmedia.backend.presence.internal;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The in-memory source of truth for who is online: user id → the ids of their open WebSocket
 * sessions (multi-device), plus a "pending offline" map for users whose last session just closed
 * and who are riding out the grace period. Session-id <em>sets</em> (not counters) make duplicate
 * disconnect events harmless.
 *
 * <p>Plain {@code HashMap}s behind {@code synchronized} methods: every operation is a few map
 * touches on connect/disconnect/sweep, so contention is negligible and the simple form is the
 * correct one.
 */
@Component
class PresenceRegistry {

    private final Map<UUID, Set<String>> sessions = new HashMap<>();
    private final Map<UUID, Instant> pendingOffline = new HashMap<>();

    /**
     * Registers a session; returns {@code true} only when this publicly transitions the user
     * online. A reconnect inside the grace window just cancels the pending offline — no transition.
     */
    synchronized boolean addSession(UUID userId, String sessionId) {
        boolean wasInGrace = pendingOffline.remove(userId) != null;
        boolean hadSessions = !sessions.computeIfAbsent(userId, id -> new HashSet<>()).isEmpty();
        sessions.get(userId).add(sessionId);
        return !hadSessions && !wasInGrace;
    }

    /** Unregisters a session; when it was the user's last, starts their offline grace window. */
    synchronized void removeSession(UUID userId, String sessionId) {
        Set<String> userSessions = sessions.get(userId);
        if (userSessions == null || !userSessions.remove(sessionId)) {
            return;
        }
        if (userSessions.isEmpty()) {
            sessions.remove(userId);
            pendingOffline.put(userId, Instant.now());
        }
    }

    /**
     * Removes and returns the users whose grace window has fully elapsed — the ones to finalize as
     * offline. Users who reconnected were already dropped from the pending map, so expiry alone is
     * the whole test.
     */
    synchronized List<UUID> sweepExpired(Duration grace) {
        Instant cutoff = Instant.now().minus(grace);
        List<UUID> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Instant>> it = pendingOffline.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Instant> entry = it.next();
            if (entry.getValue().isBefore(cutoff)) {
                expired.add(entry.getKey());
                it.remove();
            }
        }
        return expired;
    }

    /** Publicly online: has a live session, or is inside the offline grace window. */
    synchronized boolean isOnline(UUID userId) {
        return sessions.containsKey(userId) || pendingOffline.containsKey(userId);
    }

    /** Snapshot of users with at least one live session (grace-window users excluded). */
    synchronized Set<UUID> onlineUsers() {
        return new HashSet<>(sessions.keySet());
    }
}

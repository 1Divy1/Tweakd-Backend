package com.carsocialmedia.backend.presence;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user's public presence flips: {@code online == true} when their first WebSocket
 * session authenticates (and they were not inside the offline grace window), {@code online == false}
 * once their last session has been gone for the full grace period. Reconnects within the grace
 * window publish nothing at all.
 *
 * <p>{@code lastSeenAt} is set on offline transitions and {@code null} on online ones.
 */
public record UserPresenceChangedEvent(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {}

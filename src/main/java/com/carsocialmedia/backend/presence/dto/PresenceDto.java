package com.carsocialmedia.backend.presence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's presence. {@code online == true} → render "Active now" ({@code lastSeenAt} is
 * {@code null}); otherwise {@code lastSeenAt} is when they were last online, or {@code null} if
 * they have never been seen online.
 */
public record PresenceDto(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {

    public static PresenceDto online(UUID userId) {
        return new PresenceDto(userId, true, null);
    }

    public static PresenceDto offline(UUID userId, Instant lastSeenAt) {
        return new PresenceDto(userId, false, lastSeenAt);
    }
}

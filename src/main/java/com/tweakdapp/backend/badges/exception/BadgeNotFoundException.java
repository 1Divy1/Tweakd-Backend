package com.tweakdapp.backend.badges.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

/**
 * Raised when a badge code does not exist in the catalogue, or exists but is retired
 * ({@code is_available = false}) on a path that would award it.
 *
 * <p>Retired badges are refused on the write path and still resolve on the read path — a user who
 * earned one keeps seeing it, which is the whole reason retiring is a flag rather than a delete.
 */
public class BadgeNotFoundException extends NotFoundException {

    private BadgeNotFoundException(String message) {
        super(message);
    }

    public static BadgeNotFoundException byId(String badgeId) {
        return new BadgeNotFoundException("No such badge: " + badgeId);
    }

    /** Distinct message from {@link #byId}: the code is real, it just may no longer be awarded. */
    public static BadgeNotFoundException retired(String badgeId) {
        return new BadgeNotFoundException("Badge is retired and can no longer be awarded: " + badgeId);
    }
}

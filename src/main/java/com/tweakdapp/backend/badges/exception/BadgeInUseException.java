package com.tweakdapp.backend.badges.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/**
 * Raised when the dashboard tries to delete a badge that users have already unlocked.
 *
 * <p>The database would refuse it anyway — the {@code user_badges} foreign key is
 * {@code ON DELETE RESTRICT} — but a constraint violation surfacing as a 500 tells the staff member
 * nothing. Retiring the badge ({@code available = false}) is what they actually want: it stops
 * being awardable and leaves the profiles that earned it intact.
 */
public class BadgeInUseException extends ConflictException {

    public BadgeInUseException(String badgeId, long holders) {
        super("Badge '" + badgeId + "' is held by " + holders
                + " user(s) and cannot be deleted. Retire it instead (available = false).");
    }
}

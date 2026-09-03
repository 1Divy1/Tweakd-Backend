package com.tweakdapp.backend.badges.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/**
 * Raised when the dashboard creates a badge whose code is already taken.
 *
 * <p>A conflict rather than an overwrite on purpose: the code is referenced by every
 * {@code user_badges} row that earned it, so silently replacing the definition behind it would
 * rewrite what those users think they hold.
 */
public class BadgeAlreadyExistsException extends ConflictException {

    public BadgeAlreadyExistsException(String badgeId) {
        super("A badge with this code already exists: " + badgeId);
    }
}

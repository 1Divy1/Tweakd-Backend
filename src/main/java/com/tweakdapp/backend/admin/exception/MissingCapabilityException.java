package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.ForbiddenException;

/**
 * Thrown when a team member's role does not grant the capability an endpoint requires (e.g. a
 * support agent opening the moderation queue). Maps to HTTP 403.
 */
public class MissingCapabilityException extends ForbiddenException {

    public MissingCapabilityException(String capability) {
        super("Your team role does not allow this action (requires " + capability + ")");
    }
}

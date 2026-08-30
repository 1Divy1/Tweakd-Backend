package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a moderation action does not fit the case — removing "content" of a profile report,
 * acting on an already-resolved case, and similar. Maps to HTTP 400.
 */
public class InvalidModerationActionException extends BadRequestException {

    public InvalidModerationActionException(String message) {
        super(message);
    }
}

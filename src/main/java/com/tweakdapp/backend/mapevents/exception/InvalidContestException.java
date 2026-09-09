package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Raised when a contest payload is inconsistent: a window that closes before it opens or reaches
 * past the bound, an unknown category, a rejection without a reason, an unrecognised decision, or
 * a car that is not an accepted participant of the event.
 */
public class InvalidContestException extends BadRequestException {
    public InvalidContestException(String message) {
        super(message);
    }
}

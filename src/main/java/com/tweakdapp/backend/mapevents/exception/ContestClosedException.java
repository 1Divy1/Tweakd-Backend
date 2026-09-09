package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/**
 * Raised when a contest (or the event it runs in) is over for the purpose of the action: voting
 * after {@code closes_at}, editing a finished contest, pulling an accepted car out once voting has
 * opened.
 */
public class ContestClosedException extends ConflictException {
    public ContestClosedException(String message) {
        super(message);
    }
}

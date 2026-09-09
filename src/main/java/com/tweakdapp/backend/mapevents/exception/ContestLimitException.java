package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/** Raised when an event already has its 20 contests, or a contest its 40 accepted entries. */
public class ContestLimitException extends ConflictException {
    public ContestLimitException(String message) {
        super(message);
    }
}

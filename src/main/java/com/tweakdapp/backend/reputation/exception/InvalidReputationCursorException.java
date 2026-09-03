package com.tweakdapp.backend.reputation.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** Raised when a reputation-history pagination token cannot be decoded. */
public class InvalidReputationCursorException extends BadRequestException {

    public InvalidReputationCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}

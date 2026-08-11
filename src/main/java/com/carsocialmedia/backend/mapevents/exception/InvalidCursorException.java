package com.carsocialmedia.backend.mapevents.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Raised when a pagination cursor cannot be decoded. Cursors are opaque tokens the client is meant
 * to echo back untouched, so a malformed one is a client bug, not a recoverable state.
 */
public class InvalidCursorException extends BadRequestException {

    public InvalidCursorException(String cursor) {
        super("Invalid cursor: " + cursor);
    }
}

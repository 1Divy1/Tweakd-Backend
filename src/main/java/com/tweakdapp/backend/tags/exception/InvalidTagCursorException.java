package com.tweakdapp.backend.tags.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when the tags feed's pagination cursor cannot be decoded. Maps to HTTP 400 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class InvalidTagCursorException extends BadRequestException {

    public InvalidTagCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}

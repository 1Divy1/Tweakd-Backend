package com.tweakdapp.backend.forums.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a client supplies a pagination cursor that cannot be decoded. Maps to HTTP 400 via
 * the shared {@code GlobalExceptionHandler}.
 */
public class InvalidCursorException extends BadRequestException {

    public InvalidCursorException(String token) {
        super("Invalid pagination cursor: " + token);
    }
}

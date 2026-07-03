package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a client supplies a pagination cursor that cannot be decoded. Maps to HTTP 400 via
 * the shared {@code GlobalExceptionHandler}.
 */
public class InvalidCursorException extends BadRequestException {

    public InvalidCursorException(String token) {
        super("Invalid pagination cursor: " + token);
    }
}

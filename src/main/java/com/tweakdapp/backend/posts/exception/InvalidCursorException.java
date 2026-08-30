package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a pagination cursor supplied by the client cannot be decoded.
 * Maps to HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class InvalidCursorException extends BadRequestException {

    public InvalidCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}
package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a thread or shortcut references a brand, model, or topic that does not exist (or an
 * inactive topic). Maps to HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class InvalidReferenceException extends BadRequestException {

    public InvalidReferenceException(String message) {
        super(message);
    }
}

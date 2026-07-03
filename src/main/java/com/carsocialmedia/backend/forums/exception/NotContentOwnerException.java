package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/**
 * Thrown when a user attempts to delete a thread or reply they do not own. Maps to HTTP 403 via the
 * shared {@code GlobalExceptionHandler}.
 */
public class NotContentOwnerException extends ForbiddenException {

    public NotContentOwnerException() {
        super("You are not the owner of this content");
    }
}

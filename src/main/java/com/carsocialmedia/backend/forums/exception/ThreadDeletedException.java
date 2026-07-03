package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/**
 * Thrown when a user attempts to edit a thread that was deleted (anonymized) by its author. A
 * deleted thread stays readable and repliable, but its content is frozen. Maps to HTTP 409 via the
 * shared {@code GlobalExceptionHandler}.
 */
public class ThreadDeletedException extends ConflictException {

    public ThreadDeletedException() {
        super("This thread was deleted and can no longer be edited");
    }
}

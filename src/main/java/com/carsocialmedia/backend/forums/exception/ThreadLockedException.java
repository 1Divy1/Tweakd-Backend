package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/**
 * Thrown when a user attempts to add content to a locked thread — replying, or editing the thread
 * body or an existing reply. Maps to HTTP 409 via the shared {@code GlobalExceptionHandler}.
 */
public class ThreadLockedException extends ConflictException {

    public ThreadLockedException() {
        super("This thread is locked");
    }
}

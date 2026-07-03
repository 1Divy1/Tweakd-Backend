package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a thread addressed by id does not exist (or is deleted). Maps to HTTP 404 via the
 * shared {@code GlobalExceptionHandler}.
 */
public class ThreadNotFoundException extends NotFoundException {

    public ThreadNotFoundException(UUID threadId) {
        super("Thread not found: " + threadId);
    }
}

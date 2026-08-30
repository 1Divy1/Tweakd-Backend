package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/**
 * Raised when someone tries to take part in an event that is no longer open to it: RSVPing to an
 * event that has finished or been cancelled, or entering a car after the category's registration
 * deadline has passed.
 */
public class EventClosedException extends ConflictException {

    public EventClosedException(String message) {
        super(message);
    }
}

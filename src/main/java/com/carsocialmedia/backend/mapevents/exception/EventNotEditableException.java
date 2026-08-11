package com.carsocialmedia.backend.mapevents.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/**
 * Raised when an organizer tries to edit an event that is no longer editable. Approval locks an
 * event: it must not be possible to have an event approved as one thing and then quietly turn it
 * into another. Cancelled and finished events are likewise closed to edits.
 */
public class EventNotEditableException extends ConflictException {

    public EventNotEditableException(String message) {
        super(message);
    }
}

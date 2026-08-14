package com.carsocialmedia.backend.mapevents.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Raised when an event payload is internally inconsistent or refers to something that does not
 * exist — an end time before the start, a start in the past, a registration deadline after the
 * event begins, an unknown or unavailable category, an unrecognised RSVP or decision value, or an
 * organizer request naming neither (or both) of a user and a business.
 */
public class InvalidMapEventException extends BadRequestException {

    public InvalidMapEventException(String message) {
        super(message);
    }
}

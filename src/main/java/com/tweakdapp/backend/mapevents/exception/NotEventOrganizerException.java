package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ForbiddenException;

/**
 * Raised when a caller tries to perform an organizer-only action (edit, cancel, manage organizers,
 * decide on entered cars) on an event they do not organize — or a creator-only action, such as
 * deleting the event, that a mere co-organizer may not take.
 */
public class NotEventOrganizerException extends ForbiddenException {

    public NotEventOrganizerException(String message) {
        super(message);
    }
}

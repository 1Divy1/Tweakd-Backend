package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when an event does not exist, or exists but the caller is not allowed to see it — an
 * unapproved event is visible only to its organizers and to admins.
 *
 * <p>Both cases share one exception on purpose: distinguishing them would let anyone detect
 * pending and rejected submissions by probing ids.
 */
public class MapEventNotFoundException extends NotFoundException {

    public MapEventNotFoundException(UUID eventId) {
        super("Event not found: " + eventId);
    }
}

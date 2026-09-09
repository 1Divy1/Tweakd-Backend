package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a contest does not exist in the given event, or the event itself is not visible to
 * the caller. One exception for both so an unapproved event's contests cannot be probed.
 */
public class ContestNotFoundException extends NotFoundException {
    public ContestNotFoundException(UUID contestId) {
        super("Contest not found: " + contestId);
    }
}

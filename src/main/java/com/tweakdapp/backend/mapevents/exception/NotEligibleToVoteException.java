package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ForbiddenException;

/**
 * Raised when the caller may not vote in this contest: no {@code attending} RSVP on the event, or
 * the car is their own.
 */
public class NotEligibleToVoteException extends ForbiddenException {
    public NotEligibleToVoteException(String message) {
        super(message);
    }
}

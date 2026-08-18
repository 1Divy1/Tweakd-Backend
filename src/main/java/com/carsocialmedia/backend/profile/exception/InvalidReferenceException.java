package com.carsocialmedia.backend.profile.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when an onboarding/profile request references a lookup-table row (city,
 * language) that does not exist. Surfaces as 400 because the
 * failure is with the inbound payload, not a missing resource addressed by URL.
 */
public class InvalidReferenceException extends BadRequestException {

    public InvalidReferenceException(String message) {
        super(message);
    }
}
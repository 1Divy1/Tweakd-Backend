package com.tweakdapp.backend.garage.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a request references a row in a lookup table (brand, model, drivetrain, …)
 * that does not exist. Surfaces as 400 rather than 404 because the validation failure is
 * with the inbound payload, not a missing resource the caller addressed by URL.
 */
public class InvalidReferenceException extends BadRequestException {

    public InvalidReferenceException(String message) {
        super(message);
    }
}

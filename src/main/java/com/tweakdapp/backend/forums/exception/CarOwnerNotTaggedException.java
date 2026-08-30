package com.tweakdapp.backend.forums.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

import java.util.UUID;

/**
 * Thrown when a thread or reply tags a car whose owner is neither the author nor one of the people
 * tagged in the same thread/reply. A car can only be tagged once its owner is tagged (you tag the
 * user, then pick from their garage) — except for the author's own cars, which need no self-tag.
 * Maps to HTTP 400.
 */
public class CarOwnerNotTaggedException extends BadRequestException {

    public CarOwnerNotTaggedException(UUID carId) {
        super("Car " + carId + " can only be tagged if its owner is also tagged in the same thread or reply");
    }
}

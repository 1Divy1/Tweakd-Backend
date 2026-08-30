package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

import java.util.UUID;

/**
 * Thrown when a post tags a car whose owner is neither the author nor one of the tagged people.
 * A car can only be tagged once its owner is tagged in the same post (you tag the user, pick from
 * their cars) — except for the author's own cars, which need no self-tag. Maps to HTTP 400.
 */
public class CarOwnerNotTaggedException extends BadRequestException {

    public CarOwnerNotTaggedException(UUID carId) {
        super("Car " + carId + " can only be tagged if its owner is also tagged in the post");
    }
}
package com.tweakdapp.backend.garage.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a dream car does not exist or does not belong to the current user.
 * Surfaces as 404 (queries are scoped to the owner, so another user's dream car is
 * indistinguishable from a missing one).
 */
public class DreamCarNotFoundException extends NotFoundException {

    public DreamCarNotFoundException(UUID dreamCarId) {
        super("Dream car not found: " + dreamCarId);
    }
}
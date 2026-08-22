package com.tweakdapp.backend.garage.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

/**
 * Thrown when a garage for a specific user is not found.
 *
 * This should be rare since garages are auto-created for users, but surfaces as 404
 * if the garage row is missing from the database.
 */
public class GarageNotFoundException extends NotFoundException {

    private GarageNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a new exception for a garage not found for the given owner.
     *
     * @param ownerId the user ID of the garage owner
     * @return a new GarageNotFoundException
     */
    public static GarageNotFoundException forOwner(String ownerId) {
        return new GarageNotFoundException("Garage not found for user: " + ownerId);
    }
}
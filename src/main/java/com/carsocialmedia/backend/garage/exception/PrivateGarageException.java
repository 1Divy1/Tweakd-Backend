package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/**
 * Thrown when the current user attempts to view a garage of a user with a private profile
 * and the current user is not an accepted follower.
 *
 * Surfaces as 403 Forbidden. Garage visibility is tied to the profile's privacy settings:
 * if the profile is private, only the owner or accepted followers may view the garage.
 */
public class PrivateGarageException extends ForbiddenException {

    public PrivateGarageException(String username) {
        super("Garage of '" + username + "' is private");
    }
}
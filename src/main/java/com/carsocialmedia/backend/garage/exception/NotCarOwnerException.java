package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/**
 * Thrown when the current user attempts to modify a car they do not own.
 *
 * Surfaces as 403 Forbidden. Only the car owner can update, delete, or add modifications
 * to a car.
 */
public class NotCarOwnerException extends ForbiddenException {

    public NotCarOwnerException() {
        super("You are not the owner of this car");
    }
}
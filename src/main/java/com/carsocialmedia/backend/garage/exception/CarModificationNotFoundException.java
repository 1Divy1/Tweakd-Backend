package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a car modification cannot be found or does not belong to the specified car.
 *
 * Surfaces as 404 Not Found. A modification ID may be valid but belong to a different car;
 * this is also treated as not found within the context of the requested car.
 */
public class CarModificationNotFoundException extends NotFoundException {

    public CarModificationNotFoundException(UUID modificationId) {
        super("Car modification not found: " + modificationId);
    }
}

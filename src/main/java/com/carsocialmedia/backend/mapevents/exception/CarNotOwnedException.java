package com.carsocialmedia.backend.mapevents.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

import java.util.UUID;

/**
 * Raised when someone tries to enter a car they do not own into an event. Only the car's owner
 * decides where it is shown.
 */
public class CarNotOwnedException extends ForbiddenException {

    public CarNotOwnedException(UUID carId) {
        super("Car does not belong to you: " + carId);
    }
}

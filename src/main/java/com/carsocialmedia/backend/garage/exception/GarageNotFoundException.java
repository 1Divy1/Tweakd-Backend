package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

public class GarageNotFoundException extends NotFoundException {

    private GarageNotFoundException(String message) {
        super(message);
    }

    public static GarageNotFoundException forOwner(String ownerId) {
        return new GarageNotFoundException("Garage not found for user: " + ownerId);
    }
}
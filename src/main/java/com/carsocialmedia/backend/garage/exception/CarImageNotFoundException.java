package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

public class CarImageNotFoundException extends NotFoundException {
    public CarImageNotFoundException(UUID imageId) {
        super("Car image not found: " + imageId);
    }
}
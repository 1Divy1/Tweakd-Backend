package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

public class CarModificationNotFoundException extends NotFoundException {

    public CarModificationNotFoundException(UUID modificationId) {
        super("Car modification not found: " + modificationId);
    }
}

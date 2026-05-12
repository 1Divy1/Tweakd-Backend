package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

public class CarNotFoundException extends NotFoundException {

    public CarNotFoundException(UUID carId) {
        super("Car not found: " + carId);
    }
}
package com.tweakdapp.backend.garage.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

public class CarNotFoundException extends NotFoundException {

    public CarNotFoundException(UUID carId) {
        super("Car not found: " + carId);
    }
}
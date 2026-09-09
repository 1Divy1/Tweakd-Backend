package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** Raised when the car whose event history was asked for does not exist. */
public class HistoryCarNotFoundException extends NotFoundException {
    public HistoryCarNotFoundException(UUID carId) {
        super("Car not found: " + carId);
    }
}

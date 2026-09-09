package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** Raised when a car is not on a contest's ballot — not entered, or entered but not accepted. */
public class ContestEntryNotFoundException extends NotFoundException {
    public ContestEntryNotFoundException(UUID carId) {
        super("That car is not on this contest's ballot: " + carId);
    }
}

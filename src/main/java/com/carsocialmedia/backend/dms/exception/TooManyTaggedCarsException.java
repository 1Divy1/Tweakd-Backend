package com.carsocialmedia.backend.dms.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/** A single DM may tag at most a fixed number of cars. */
public class TooManyTaggedCarsException extends BadRequestException {

    public TooManyTaggedCarsException(int max) {
        super("A message can tag at most " + max + " cars");
    }
}

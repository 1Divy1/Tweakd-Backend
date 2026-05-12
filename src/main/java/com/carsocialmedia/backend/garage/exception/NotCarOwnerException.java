package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

public class NotCarOwnerException extends ForbiddenException {

    public NotCarOwnerException() {
        super("You are not the owner of this car");
    }
}
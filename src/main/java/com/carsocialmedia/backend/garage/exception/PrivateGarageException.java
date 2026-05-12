package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

public class PrivateGarageException extends ForbiddenException {

    public PrivateGarageException(String username) {
        super("Garage of '" + username + "' is private");
    }
}
package com.carsocialmedia.backend.profile.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

public class UsernameAlreadyTakenException extends ConflictException {

    public UsernameAlreadyTakenException(String username) {
        super("Username '" + username + "' is already taken");
    }
}

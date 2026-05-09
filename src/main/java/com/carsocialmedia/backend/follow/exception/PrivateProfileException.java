package com.carsocialmedia.backend.follow.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

public class PrivateProfileException extends ForbiddenException {

    public PrivateProfileException(String username) {
        super("Profile '" + username + "' is private");
    }
}

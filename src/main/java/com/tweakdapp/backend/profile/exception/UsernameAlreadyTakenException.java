package com.tweakdapp.backend.profile.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

public class UsernameAlreadyTakenException extends ConflictException {

    public UsernameAlreadyTakenException(String username) {
        super("Username '" + username + "' is already taken");
    }
}

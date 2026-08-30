package com.tweakdapp.backend.relationships.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

public class CannotFollowSelfException extends BadRequestException {

    public CannotFollowSelfException() {
        super("You cannot follow yourself");
    }
}

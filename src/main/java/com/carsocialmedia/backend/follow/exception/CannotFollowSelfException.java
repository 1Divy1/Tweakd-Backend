package com.carsocialmedia.backend.follow.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

public class CannotFollowSelfException extends BadRequestException {

    public CannotFollowSelfException() {
        super("You cannot follow yourself");
    }
}

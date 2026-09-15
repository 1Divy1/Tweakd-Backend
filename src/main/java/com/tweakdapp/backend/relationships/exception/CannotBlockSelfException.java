package com.tweakdapp.backend.relationships.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

public class CannotBlockSelfException extends BadRequestException {

    public CannotBlockSelfException() {
        super("You cannot block yourself");
    }
}

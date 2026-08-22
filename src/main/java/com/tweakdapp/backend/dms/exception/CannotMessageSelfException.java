package com.tweakdapp.backend.dms.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** DMing yourself is not a thing. */
public class CannotMessageSelfException extends BadRequestException {

    public CannotMessageSelfException() {
        super("You cannot send a message to yourself");
    }
}

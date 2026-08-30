package com.tweakdapp.backend.support.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** A pagination cursor on a ticket list could not be decoded. */
public class InvalidTicketCursorException extends BadRequestException {

    public InvalidTicketCursorException(String token) {
        super("Invalid pagination cursor: " + token);
    }
}

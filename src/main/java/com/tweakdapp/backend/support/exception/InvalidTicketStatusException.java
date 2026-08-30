package com.tweakdapp.backend.support.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** Status must be one of open / awaiting_user / resolved. */
public class InvalidTicketStatusException extends BadRequestException {

    public InvalidTicketStatusException(String status) {
        super("Invalid ticket status: " + status);
    }
}

package com.tweakdapp.backend.support.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** Priority must be one of low / normal / high / urgent. */
public class InvalidTicketPriorityException extends BadRequestException {

    public InvalidTicketPriorityException(String priority) {
        super("Invalid ticket priority: " + priority);
    }
}

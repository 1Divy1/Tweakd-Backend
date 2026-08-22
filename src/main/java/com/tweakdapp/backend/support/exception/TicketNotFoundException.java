package com.tweakdapp.backend.support.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The ticket does not exist — or belongs to another user, which is deliberately the same 404. */
public class TicketNotFoundException extends NotFoundException {

    public TicketNotFoundException(UUID ticketId) {
        super("Support ticket not found: " + ticketId);
    }
}

package com.carsocialmedia.backend.dms.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The message's recipient does not resolve to an existing profile. */
public class DmRecipientNotFoundException extends NotFoundException {

    public DmRecipientNotFoundException(UUID recipientId) {
        super("Recipient not found: " + recipientId);
    }
}

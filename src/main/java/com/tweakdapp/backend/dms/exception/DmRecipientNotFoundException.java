package com.tweakdapp.backend.dms.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The message's recipient does not resolve to an existing profile. */
public class DmRecipientNotFoundException extends NotFoundException {

    public DmRecipientNotFoundException(UUID recipientId) {
        super("Recipient not found: " + recipientId);
    }
}

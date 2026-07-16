package com.carsocialmedia.backend.dms.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The message does not exist — or the caller is not its sender, which is deliberately the same 404. */
public class DmMessageNotFoundException extends NotFoundException {

    public DmMessageNotFoundException(UUID messageId) {
        super("Message not found: " + messageId);
    }
}

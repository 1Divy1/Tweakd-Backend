package com.tweakdapp.backend.business.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** Raised when the review queue is handed a {@code ?cursor=} token it cannot parse. */
public class InvalidBusinessCursorException extends BadRequestException {

    public InvalidBusinessCursorException(String token) {
        super("Invalid cursor: " + token);
    }
}

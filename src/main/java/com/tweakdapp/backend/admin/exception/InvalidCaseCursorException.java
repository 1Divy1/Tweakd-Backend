package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** Thrown when a moderation-queue cursor cannot be decoded. Maps to HTTP 400. */
public class InvalidCaseCursorException extends BadRequestException {

    public InvalidCaseCursorException() {
        super("Invalid pagination cursor");
    }
}

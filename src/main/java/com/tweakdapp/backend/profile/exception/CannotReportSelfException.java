package com.tweakdapp.backend.profile.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a user attempts to report their own profile. Maps to HTTP 400 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class CannotReportSelfException extends BadRequestException {

    public CannotReportSelfException() {
        super("You cannot report your own profile");
    }
}

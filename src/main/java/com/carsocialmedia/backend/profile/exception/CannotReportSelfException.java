package com.carsocialmedia.backend.profile.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a user attempts to report their own profile. Maps to HTTP 400 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class CannotReportSelfException extends BadRequestException {

    public CannotReportSelfException() {
        super("You cannot report your own profile");
    }
}

package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a user attempts to report their own thread or reply. Reporting your own content is
 * almost always noise, so it is rejected. Maps to HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class CannotReportOwnForumContentException extends BadRequestException {

    public CannotReportOwnForumContentException() {
        super("You cannot report your own content");
    }
}

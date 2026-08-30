package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a user attempts to report their own post or comment. Reporting your own content is
 * almost always noise, so it is rejected. Maps to HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class CannotReportOwnContentException extends BadRequestException {

    public CannotReportOwnContentException() {
        super("You cannot report your own content");
    }
}

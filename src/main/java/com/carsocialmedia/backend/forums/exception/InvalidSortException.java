package com.carsocialmedia.backend.forums.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a client supplies an unrecognized {@code ?sort=} value, so a typo fails loudly
 * instead of silently falling back to the default ordering. Maps to HTTP 400 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class InvalidSortException extends BadRequestException {

    public InvalidSortException(String raw) {
        super("Unknown sort: '" + raw + "' (expected one of: hot, new, active)");
    }

    /** For contexts with a different set of valid sorts (e.g. reply lists: {@code old, new}). */
    public InvalidSortException(String raw, String expected) {
        super("Unknown sort: '" + raw + "' (expected one of: " + expected + ")");
    }
}

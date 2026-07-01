package com.carsocialmedia.backend.report.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

import java.util.UUID;

/**
 * Thrown when a supplied {@code reasonId} does not reference a {@code report_reasons} row, or the
 * referenced reason is scoped to a different target than the thing being reported (e.g. a
 * {@code profile} reason used to report a post). Maps to HTTP 400 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class InvalidReportReasonException extends BadRequestException {

    public InvalidReportReasonException(UUID reasonId) {
        super("Invalid report reason for this target: " + reasonId);
    }
}

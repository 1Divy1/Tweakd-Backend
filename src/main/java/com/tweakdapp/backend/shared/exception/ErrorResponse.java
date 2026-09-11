package com.tweakdapp.backend.shared.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp,
        String error,
        Map<String, Object> details
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, null, Instant.now(), null, null);
    }

    public static ErrorResponse of(int status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, message, fieldErrors, Instant.now(), null, null);
    }

    /**
     * An {@link ApiException}'s body: status and message, plus its machine-readable {@code error}
     * code and structured {@code details} when it has them (both {@code null} otherwise).
     */
    public static ErrorResponse of(ApiException ex) {
        return new ErrorResponse(ex.getStatus().value(), ex.getMessage(), null, Instant.now(),
                ex.getErrorCode(), ex.getDetails());
    }
}

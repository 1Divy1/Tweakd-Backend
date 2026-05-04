package com.carsocialmedia.backend.shared.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, null, Instant.now());
    }

    public static ErrorResponse of(int status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, message, fieldErrors, Instant.now());
    }
}

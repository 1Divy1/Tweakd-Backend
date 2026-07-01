package com.carsocialmedia.backend.feedback.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when the submitted {@code type} does not reference a {@code feedback_type_options} row.
 * Maps to HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class InvalidFeedbackTypeException extends BadRequestException {

    public InvalidFeedbackTypeException(String type) {
        super("Invalid feedback type: " + type);
    }
}

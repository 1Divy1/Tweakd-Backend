package com.tweakdapp.backend.feedback.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a supplied {@code feature} does not reference a {@code feedback_feature_options} row.
 * Maps to HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class InvalidFeedbackFeatureException extends BadRequestException {

    public InvalidFeedbackFeatureException(String feature) {
        super("Invalid feedback feature: " + feature);
    }
}

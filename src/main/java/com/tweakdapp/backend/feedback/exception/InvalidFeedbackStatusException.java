package com.tweakdapp.backend.feedback.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** The supplied status id doesn't reference a {@code feedback_status_options} row. */
public class InvalidFeedbackStatusException extends BadRequestException {

    public InvalidFeedbackStatusException(String statusId) {
        super("Invalid feedback status: " + statusId);
    }
}

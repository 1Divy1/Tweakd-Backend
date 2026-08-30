package com.tweakdapp.backend.feedback.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** A pagination cursor on a feedback-board endpoint could not be decoded. */
public class InvalidFeedbackCursorException extends BadRequestException {

    public InvalidFeedbackCursorException(String token) {
        super("Invalid pagination cursor: " + token);
    }
}

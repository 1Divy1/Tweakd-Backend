package com.tweakdapp.backend.feedbackfeed.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** Raised for an unknown category or status id, an unrecognised sort, or a vote value other than ±1. */
public class InvalidFeedbackFeedRequestException extends BadRequestException {

    public InvalidFeedbackFeedRequestException(String message) {
        super(message);
    }
}

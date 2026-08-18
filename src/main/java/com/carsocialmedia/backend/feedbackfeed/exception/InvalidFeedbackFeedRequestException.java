package com.carsocialmedia.backend.feedbackfeed.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/** Raised for an unknown category or status id, an unrecognised sort, or a vote value other than ±1. */
public class InvalidFeedbackFeedRequestException extends BadRequestException {

    public InvalidFeedbackFeedRequestException(String message) {
        super(message);
    }
}

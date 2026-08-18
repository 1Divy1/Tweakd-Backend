package com.carsocialmedia.backend.feedbackfeed.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/** Raised when a user tries to delete a feedback message somebody else wrote. */
public class NotFeedbackAuthorException extends ForbiddenException {

    public NotFeedbackAuthorException() {
        super("Only the author can delete this feedback message");
    }
}

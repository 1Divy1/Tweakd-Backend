package com.carsocialmedia.backend.feedbackfeed.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a feedback message does not exist, or has been removed by staff.
 *
 * <p>Both cases share one exception on purpose: a distinct "removed" response would let anyone
 * confirm which messages were taken down for spam or abuse.
 */
public class FeedbackMessageNotFoundException extends NotFoundException {

    public FeedbackMessageNotFoundException(UUID messageId) {
        super("Feedback message not found: " + messageId);
    }
}

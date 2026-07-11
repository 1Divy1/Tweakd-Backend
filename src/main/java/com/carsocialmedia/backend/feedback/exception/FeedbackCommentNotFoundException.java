package com.carsocialmedia.backend.feedback.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The comment does not exist or does not belong to the addressed feedback. */
public class FeedbackCommentNotFoundException extends NotFoundException {

    public FeedbackCommentNotFoundException(UUID commentId) {
        super("Feedback comment not found: " + commentId);
    }
}

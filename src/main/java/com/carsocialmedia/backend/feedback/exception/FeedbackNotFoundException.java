package com.carsocialmedia.backend.feedback.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

public class FeedbackNotFoundException extends NotFoundException {

    public FeedbackNotFoundException(UUID feedbackId) {
        super("Feedback not found: " + feedbackId);
    }
}

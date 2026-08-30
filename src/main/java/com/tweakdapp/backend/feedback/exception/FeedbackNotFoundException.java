package com.tweakdapp.backend.feedback.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

public class FeedbackNotFoundException extends NotFoundException {

    public FeedbackNotFoundException(UUID feedbackId) {
        super("Feedback not found: " + feedbackId);
    }
}

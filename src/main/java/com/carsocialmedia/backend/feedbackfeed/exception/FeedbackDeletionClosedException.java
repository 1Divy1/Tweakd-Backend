package com.carsocialmedia.backend.feedbackfeed.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/**
 * Raised when an author tries to delete their own message after staff have picked it up.
 *
 * <p>Deleting is only allowed while the message is still {@code sent}. Once it moves to
 * {@code under_development} or {@code completed} it is a public roadmap entry backed by other
 * users' votes, and pulling it out from under them would erase that history. Staff can still
 * remove it — that path is a soft delete, so the row survives for audit.
 */
public class FeedbackDeletionClosedException extends ConflictException {

    public FeedbackDeletionClosedException() {
        super("This feedback is already on the roadmap and can no longer be deleted");
    }
}

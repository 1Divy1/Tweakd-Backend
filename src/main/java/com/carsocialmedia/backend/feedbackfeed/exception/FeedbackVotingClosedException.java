package com.carsocialmedia.backend.feedbackfeed.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/**
 * Raised when a vote is cast on — or withdrawn from — a message that has already shipped.
 *
 * <p>A Supabase trigger rejects these writes at the table, which would surface as an opaque SQL
 * error and poison the transaction. The service checks the status first so the caller gets this
 * instead.
 */
public class FeedbackVotingClosedException extends ConflictException {

    public FeedbackVotingClosedException() {
        super("This feedback has been completed; voting is closed");
    }
}

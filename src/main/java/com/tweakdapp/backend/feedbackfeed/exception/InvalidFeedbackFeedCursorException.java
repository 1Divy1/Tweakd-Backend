package com.tweakdapp.backend.feedbackfeed.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Raised when a pagination token cannot be decoded, or when it was issued for a different sort than
 * the one it is being replayed against — a "popular" cursor carries a vote score, which is
 * meaningless to the time-ordered feeds.
 */
public class InvalidFeedbackFeedCursorException extends BadRequestException {

    public InvalidFeedbackFeedCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}

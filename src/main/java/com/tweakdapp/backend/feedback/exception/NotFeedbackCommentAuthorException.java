package com.tweakdapp.backend.feedback.exception;

import com.tweakdapp.backend.shared.exception.ForbiddenException;

/** Users may only delete their own feedback-board comments. */
public class NotFeedbackCommentAuthorException extends ForbiddenException {

    public NotFeedbackCommentAuthorException() {
        super("You can only delete your own comments");
    }
}

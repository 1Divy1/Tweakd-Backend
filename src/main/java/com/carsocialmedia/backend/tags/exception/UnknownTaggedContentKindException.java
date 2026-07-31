package com.carsocialmedia.backend.tags.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when the untag endpoint is addressed with a content kind that does not exist. Maps to
 * HTTP 400 via the shared {@code GlobalExceptionHandler}.
 */
public class UnknownTaggedContentKindException extends BadRequestException {

    public UnknownTaggedContentKindException(String kind) {
        super("Unknown tagged content kind: " + kind
                + " (expected one of: post, post_comment, forum_thread, forum_reply)");
    }
}

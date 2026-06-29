package com.carsocialmedia.backend.posts.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/**
 * Thrown when a user attempts to delete a comment they may not delete — i.e. they are neither the
 * comment's author nor the owner of the post it sits on. Maps to HTTP 403 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class NotCommentOwnerException extends ForbiddenException {

    public NotCommentOwnerException() {
        super("You can only delete your own comments or comments on your post");
    }
}
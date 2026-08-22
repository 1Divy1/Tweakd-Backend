package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.ForbiddenException;

/**
 * Thrown when a user attempts to mutate a post they do not own. Maps to HTTP 403 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class NotPostOwnerException extends ForbiddenException {

    public NotPostOwnerException() {
        super("You are not the owner of this post");
    }
}
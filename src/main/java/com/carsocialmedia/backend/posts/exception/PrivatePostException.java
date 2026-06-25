package com.carsocialmedia.backend.posts.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/**
 * Thrown when the current user attempts to view a post by a private author and is not an
 * accepted follower. Mirrors the garage / profile privacy gate: a private author's content is
 * visible only to the author or their accepted followers. Maps to HTTP 403.
 */
public class PrivatePostException extends ForbiddenException {

    public PrivatePostException() {
        super("This post belongs to a private account you do not follow");
    }
}

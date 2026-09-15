package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a user tries to repost their own post. A repost puts someone else's post in front of
 * your followers; your own posts are already there. Maps to HTTP 400 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class CannotRepostOwnPostException extends BadRequestException {

    public CannotRepostOwnPostException() {
        super("You cannot repost your own post");
    }
}

package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a comment addressed by id does not exist (or does not belong to the post in the
 * URL). Maps to HTTP 404 via the shared {@code GlobalExceptionHandler}.
 */
public class CommentNotFoundException extends NotFoundException {

    public CommentNotFoundException(UUID commentId) {
        super("Comment not found: " + commentId);
    }
}
package com.tweakdapp.backend.forums.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a reply addressed by id does not exist, or does not belong to the thread it was
 * addressed under. Maps to HTTP 404 via the shared {@code GlobalExceptionHandler}.
 */
public class ForumPostNotFoundException extends NotFoundException {

    public ForumPostNotFoundException(UUID postId) {
        super("Reply not found: " + postId);
    }
}

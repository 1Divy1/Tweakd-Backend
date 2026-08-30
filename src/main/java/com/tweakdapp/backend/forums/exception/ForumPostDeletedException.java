package com.tweakdapp.backend.forums.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

import java.util.UUID;

/**
 * Thrown when a user attempts to interact with a soft-deleted reply in a way that is no longer
 * allowed: replying to it, liking it, or editing it. The "[deleted]" placeholder only anchors its
 * surviving children. Maps to HTTP 409 via the shared {@code GlobalExceptionHandler}.
 */
public class ForumPostDeletedException extends ConflictException {

    public ForumPostDeletedException(UUID postId) {
        super("Reply was deleted: " + postId);
    }
}

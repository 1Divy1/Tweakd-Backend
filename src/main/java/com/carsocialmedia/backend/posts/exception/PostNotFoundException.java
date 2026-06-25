package com.carsocialmedia.backend.posts.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a post addressed by id does not exist. Maps to HTTP 404 via the shared
 * {@code GlobalExceptionHandler}.
 */
public class PostNotFoundException extends NotFoundException {

    public PostNotFoundException(UUID postId) {
        super("Post not found: " + postId);
    }
}
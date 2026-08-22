package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a post request references a profile or car that does not exist (e.g. a tagged
 * person or tagged car). Surfaces as 400 rather than 404 because the failure is with the inbound
 * payload, not a resource the caller addressed by URL.
 */
public class InvalidReferenceException extends BadRequestException {

    public InvalidReferenceException(String message) {
        super(message);
    }
}
package com.tweakdapp.backend.business.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Raised when a map search is given coordinates outside the WGS84 range, or a radius / result limit
 * outside the accepted bounds. Bounding these protects the database: an unbounded radius on a
 * spatial index degrades into a full scan of every business in the world.
 */
public class InvalidSearchAreaException extends BadRequestException {

    public InvalidSearchAreaException(String message) {
        super(message);
    }
}

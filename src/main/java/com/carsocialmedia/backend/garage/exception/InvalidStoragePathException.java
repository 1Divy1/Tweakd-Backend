package com.carsocialmedia.backend.garage.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a storage path supplied by the client is malformed or does not match the
 * canonical {@code car-photos/{ownerId}/{carId}/...} layout. Surfaces as 400.
 */
public class InvalidStoragePathException extends BadRequestException {

    public InvalidStoragePathException(String message) {
        super(message);
    }
}

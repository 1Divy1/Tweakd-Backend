package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when an ownership transfer cannot make sense: the caller is not actually the owner row,
 * or the target is the caller. Maps to HTTP 400.
 */
public class InvalidOwnershipTransferException extends BadRequestException {

    public InvalidOwnershipTransferException(String message) {
        super(message);
    }
}

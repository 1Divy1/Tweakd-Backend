package com.tweakdapp.backend.business.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Raised when a review request names a status the reference tables do not know, or asks for a
 * transition that makes no sense (rejecting without a reason, suspending a business that was never
 * verified). Validating here rather than letting the FK reject it keeps the API's error readable
 * and stops a typo'd status filter from silently returning an empty queue.
 */
public class InvalidBusinessStatusException extends BadRequestException {

    public InvalidBusinessStatusException(String message) {
        super(message);
    }
}

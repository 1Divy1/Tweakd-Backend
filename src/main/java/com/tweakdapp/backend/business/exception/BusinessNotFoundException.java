package com.tweakdapp.backend.business.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a business does not exist, or exists but is not active and verified. Both cases share
 * one exception on purpose: distinguishing them would let a caller detect suspended or
 * pending businesses by probing ids.
 */
public class BusinessNotFoundException extends NotFoundException {

    public BusinessNotFoundException(UUID businessId) {
        super("Business not found: " + businessId);
    }
}

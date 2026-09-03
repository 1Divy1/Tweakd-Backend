package com.tweakdapp.backend.reputation.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

/**
 * Raised when an award quotes a reason code that the {@code reputation_score_reason_options} catalogue does not
 * hold, or one that has been retired ({@code is_active = false}).
 *
 * <p>Retired reasons are rejected on the write path but still resolve on the read path — existing
 * history rows earned under them stay visible, which is the whole point of keeping the row rather
 * than deleting it.
 */
public class ReputationReasonNotFoundException extends NotFoundException {

    private ReputationReasonNotFoundException(String message) {
        super(message);
    }

    public static ReputationReasonNotFoundException byId(String reasonId) {
        return new ReputationReasonNotFoundException("Unknown or retired reputation reason: " + reasonId);
    }
}

package com.tweakdapp.backend.reputation.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Raised when an award or revocation is well-formed but meaningless — a point value of zero, or a
 * revocation with nothing identifying which award to take back.
 *
 * <p>The history timeline's whole value is that every line is something the user actually did and
 * was worth something; an entry that moves the score by nothing is noise in it.
 */
public class InvalidReputationAwardException extends BadRequestException {

    public InvalidReputationAwardException(String message) {
        super(message);
    }

    public static InvalidReputationAwardException zeroPoints(String reasonId) {
        return new InvalidReputationAwardException(
                "A reputation award must move the score; got 0 points for reason: " + reasonId);
    }

    /**
     * A revocation has to name the exact award it takes back. Without a source there is no way to
     * tell which of a repeatable reason's entries was meant, and guessing would subtract points
     * from the wrong achievement.
     */
    public static InvalidReputationAwardException revocationNeedsSource(String reasonId) {
        return new InvalidReputationAwardException(
                "Revoking reputation requires the source that earned it; none given for reason: " + reasonId);
    }
}

package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

import java.time.Instant;
import java.util.Map;

/**
 * Thrown when a participant card is shared again inside its repost cooldown. Surfaces as 409 with
 * {@code error = participant_card_cooldown} and {@code details.next_post_allowed_at} (ISO-8601), so
 * the app can say exactly when the card may be shared again.
 */
public class ParticipantCardCooldownException extends ConflictException {

    public static final String CODE = "participant_card_cooldown";

    private final Instant nextPostAllowedAt;

    public ParticipantCardCooldownException(Instant nextPostAllowedAt) {
        super("This card was shared recently; it can be shared again after " + nextPostAllowedAt);
        this.nextPostAllowedAt = nextPostAllowedAt;
    }

    public Instant getNextPostAllowedAt() {
        return nextPostAllowedAt;
    }

    @Override
    public String getErrorCode() {
        return CODE;
    }

    @Override
    public Map<String, Object> getDetails() {
        return Map.of("next_post_allowed_at", nextPostAllowedAt.toString());
    }
}

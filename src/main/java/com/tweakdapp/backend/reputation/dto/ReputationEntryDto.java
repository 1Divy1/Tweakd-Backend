package com.tweakdapp.backend.reputation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

/**
 * One dated entry in a user's reputation history — "what they did, when, and what it was worth".
 *
 * <p>The reason's {@code label} and {@code category} are denormalised onto the entry so rendering
 * the timeline needs no second lookup, and {@code previousScore} / {@code newScore} are the values
 * recorded at the time, not recomputed — an entry always shows the score as it stood the day it was
 * earned, even if the catalogue's point values change later.
 *
 * @param id            the history row id
 * @param reasonId      the catalogue code this entry was awarded under
 * @param label         the reason's display sentence, e.g. "Attended a car event"
 * @param category      the reason's category
 * @param scoreGain     the delta actually applied — negative for a penalty, and already clamped if
 *                      the penalty would have taken the score below zero
 * @param previousScore the user's total before this entry
 * @param newScore      the user's total after it
 * @param sourceType    what kind of thing earned it ({@code car_event}, {@code contest}, …), or
 *                      {@code null} for an entry with no source
 * @param sourceId      that thing's id, for deep-linking from the timeline; {@code null} with
 *                      {@code sourceType}
 * @param sourceLabel   the source's name as it stood when the points were awarded — a snapshot, so
 *                      it survives the source being renamed or deleted. May be {@code null}, in
 *                      which case render {@code label} alone
 * @param revokedAt     when this award was taken back, or {@code null} while it stands. Always
 *                      {@code null} on a public timeline, which omits revoked entries outright
 * @param revokedReason why it was taken back; only ever populated for the owner's own view
 * @param createdAt     when it was earned
 */
public record ReputationEntryDto(
        UUID id,
        String reasonId,
        String label,
        String category,
        int scoreGain,
        int previousScore,
        int newScore,
        String sourceType,
        UUID sourceId,
        String sourceLabel,
        Instant revokedAt,
        String revokedReason,
        Instant createdAt
) {

    /**
     * Whether this award has been taken back and no longer counts toward the score.
     *
     * <p>{@code @JsonIgnore} because Jackson would otherwise publish it as a redundant
     * {@code revoked} field alongside {@code revoked_at}. Java-side convenience only.
     */
    @JsonIgnore
    public boolean isRevoked() {
        return revokedAt != null;
    }
}

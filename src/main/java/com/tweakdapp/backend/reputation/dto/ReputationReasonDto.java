package com.tweakdapp.backend.reputation.dto;

/**
 * One entry in the reputation catalogue — a way to gain (or lose) reputation.
 *
 * <p>Reference data, read from {@code reputation_score_reason_options}. The client renders the active list as
 * the "how reputation works" sheet, which doubles as the promise the score is measured against.
 *
 * @param id           the stable code recorded on every history row for this reason
 * @param label        the display sentence, e.g. "Attended a car event" — the only prose a reason
 *                     carries, which is why there is no separate description
 * @param points       the default delta — negative for a {@code moderation} penalty, never zero
 * @param category     grouping for the UI: {@code events}, {@code contests}, {@code community},
 *                     {@code garage}, {@code trust}, {@code marketplace} or {@code moderation}
 * @param isRepeatable {@code false} for one-time achievements, which can only ever be awarded once
 */
public record ReputationReasonDto(
        String id,
        String label,
        int points,
        String category,
        boolean isRepeatable
) {}

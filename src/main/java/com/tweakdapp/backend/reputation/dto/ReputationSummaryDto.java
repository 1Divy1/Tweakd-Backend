package com.tweakdapp.backend.reputation.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The reputation block on a profile screen: the headline number plus enough shape to render it
 * without pulling the whole history.
 *
 * @param userId          whose reputation this is
 * @param score           the current total, straight off {@code profiles.reputation_score}
 * @param achievements    how many history entries built it
 * @param pointsByCategory earned points per category ({@code events}, {@code community}, …), for
 *                        the breakdown bar. Categories the user has nothing in are absent, and
 *                        {@code moderation} appears as a negative total when penalties exist
 * @param lastEarnedAt    when the most recent entry landed, or {@code null} if there are none —
 *                        lets the client show "active recently" next to a high score
 */
public record ReputationSummaryDto(
        UUID userId,
        int score,
        long achievements,
        Map<String, Integer> pointsByCategory,
        Instant lastEarnedAt
) {}

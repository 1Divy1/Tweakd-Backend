package com.tweakdapp.backend.mapevents.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A podium place a car earned in a finished contest — the car's "badge".
 *
 * @param contestId         the contest
 * @param title             its title
 * @param category          its category (the glyph the badge is drawn with)
 * @param finalRank         1, 2 or 3
 * @param finalVotesCount   the car's votes when the contest closed
 * @param contestVotesCount every vote cast in that contest
 * @param finishedAt        when the result became final
 */
public record CarEventPlacementDto(
        UUID contestId,
        String title,
        ContestCategoryDto category,
        int finalRank,
        int finalVotesCount,
        int contestVotesCount,
        Instant finishedAt
) {}

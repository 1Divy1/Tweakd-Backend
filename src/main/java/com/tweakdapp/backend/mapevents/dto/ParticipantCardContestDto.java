package com.tweakdapp.backend.mapevents.dto;

import java.util.UUID;

/**
 * One contest a car was entered in, as its participant card lists it.
 *
 * @param contestId the contest
 * @param title     display title
 * @param category  its category
 * @param finalRank the car's frozen podium place (1–3), or {@code null} when it entered but placed
 *                  nowhere — 4th or lower, or a contest nobody voted in
 */
public record ParticipantCardContestDto(
        UUID contestId,
        String title,
        ContestCategoryDto category,
        Integer finalRank
) {}

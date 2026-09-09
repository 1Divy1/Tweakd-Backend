package com.tweakdapp.backend.mapevents.dto;

import com.tweakdapp.backend.garage.dto.CarSummaryDto;

import java.time.Instant;

/**
 * One car on a contest's ballot, in ranked order.
 *
 * @param car        the car (brand, model, cover image, owner)
 * @param votesCount its live vote count
 * @param rank       its live position, 1 = leading; computed from the ordering rule (votes,
 *                   then whoever reached the count first)
 * @param finalRank  its frozen position once the contest is finished, else {@code null}
 * @param lastVoteAt when it most recently gained a vote — the tie-break, carried so the app can
 *                   re-rank a live board exactly as the server would; {@code null} with no votes
 */
public record ContestEntryDto(
        CarSummaryDto car,
        int votesCount,
        int rank,
        Integer finalRank,
        Instant lastVoteAt
) {}

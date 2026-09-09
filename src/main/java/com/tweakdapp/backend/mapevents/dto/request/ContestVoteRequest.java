package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Casts or changes the caller's vote in a contest.
 *
 * @param carId the car to vote for; must be an accepted entry the caller does not own
 */
public record ContestVoteRequest(@NotNull UUID carId) {}

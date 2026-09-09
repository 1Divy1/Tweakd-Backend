package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Asks to enter one of the caller's own cars into a contest. The car must already be an accepted
 * participant of the event.
 *
 * @param carId the car; must be owned by the caller
 */
public record ContestEntryRequest(@NotNull UUID carId) {}

package com.tweakdapp.backend.mapevents.dto;

import com.tweakdapp.backend.garage.dto.CarSummaryDto;

import java.time.Instant;

/**
 * A car waiting for an organizer's decision on its entry request. Organizers only.
 *
 * @param car         the car asking in
 * @param requestedAt when the owner asked
 */
public record ContestPendingEntryDto(CarSummaryDto car, Instant requestedAt) {}

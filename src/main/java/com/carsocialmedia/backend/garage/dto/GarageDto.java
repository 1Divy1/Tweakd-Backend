package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A user's garage and its contents (car collection).
 *
 * @param id the garage ID
 * @param ownerId the UUID of the garage owner (profile.id)
 * @param createdAt when the garage was created (managed by Supabase)
 * @param cars list of car summaries in the garage (compact projection for list view)
 */
public record GarageDto(
        UUID id,
        UUID ownerId,
        Instant createdAt,
        List<CarSummaryDto> cars
) {}
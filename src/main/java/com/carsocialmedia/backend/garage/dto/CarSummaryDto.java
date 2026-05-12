package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact car projection for the garage list view. Avoids paying for the full reference
 * lookups when only the cover, brand+model badge, and year are shown.
 */
public record CarSummaryDto(
        UUID id,
        String brand,
        String model,
        int year,
        String coverImageUrl,
        Instant createdAt
) {}
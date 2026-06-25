package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

/**
 * Compact car projection for the garage list view. Returns only the essentials needed
 * to display a car in a list (cover image and brand+model badge). Avoids the cost of
 * full reference lookups and detailed specs needed only in detail view.
 *
 * @param id the car ID
 * @param brand the car brand name
 * @param model the car model name
 * @param coverImage the car's cover image as a {key, url} pair (null if none)
 * @param status the car's status option (e.g., daily driver, weekend cruiser)
 */
public record CarSummaryDto(
        UUID id,
        String brand,
        String model,
        MediaRefDto coverImage,
        CarStatusOptionDto status
) {}
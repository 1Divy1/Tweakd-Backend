package com.tweakdapp.backend.garage.dto;

import java.util.UUID;

/**
 * Compact car projection for the garage list view. Returns only the essentials needed
 * to display a car in a list (cover image, brand+model badge, and the headline
 * year/power/torque specs shown on the card). Avoids the cost of full reference lookups
 * and detailed specs needed only in detail view.
 *
 * @param id the car ID
 * @param brand the car brand name
 * @param model the car model name
 * @param year the year the car was manufactured
 * @param horsepower engine horsepower
 * @param torque engine torque in Nm (0 for electric cars)
 * @param coverImage the car's cover image as a {key, url} pair (null if none)
 * @param status the car's status option (e.g., daily driver, weekend cruiser)
 * @param owner the profile that owns the car (id + username)
 */
public record CarSummaryDto(
        UUID id,
        String brand,
        String model,
        int year,
        int horsepower,
        int torque,
        MediaRefDto coverImage,
        CarStatusOptionDto status,
        CarOwnerDto owner
) {}
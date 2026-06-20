package com.carsocialmedia.backend.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Granular update of a user's location: their city and discovery radius. All fields are
 * optional so a client can update only what changed.
 */
public record LocationRequest(
        // TODO: The location request should allow the user to change the region and country too
        String cityId,

        @Min(value = 1, message = "Discovery radius must be between 1 and 100 km")
        @Max(value = 100, message = "Discovery radius must be between 1 and 100 km")
        Integer discoveryRadiusKm
) {}
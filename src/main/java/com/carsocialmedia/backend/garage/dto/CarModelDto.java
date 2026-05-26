package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

/**
 * A car model produced by a brand (e.g., BMW 3 Series, Honda Civic).
 *
 * @param id the model ID (UUID)
 * @param brandId the ID of the brand that produces this model
 * @param model the model name
 */
public record CarModelDto(UUID id, UUID brandId, String model) {}
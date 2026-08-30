package com.tweakdapp.backend.garage.dto;

import java.util.UUID;

/**
 * A car model produced by a brand (e.g., BMW 3 Series, Honda Civic).
 *
 * @param id the model ID (UUID)
 * @param brandId the ID of the brand that produces this model
 * @param model the model name
 * @param threadCount number of forum threads scoped to this model (for the forums hubs/suggestions)
 */
public record CarModelDto(UUID id, UUID brandId, String model, int threadCount) {}
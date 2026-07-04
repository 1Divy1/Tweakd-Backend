package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

/**
 * A car manufacturer brand (e.g., BMW, Honda, Tesla).
 *
 * @param id the brand ID (UUID)
 * @param name the brand name
 * @param threadCount number of forum threads scoped to this brand (for the forums hubs/suggestions)
 */
public record CarBrandDto(UUID id, String name, int threadCount) {}
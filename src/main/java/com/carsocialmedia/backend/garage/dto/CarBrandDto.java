package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

/**
 * A car manufacturer brand (e.g., BMW, Honda, Tesla).
 *
 * @param id the brand ID (UUID)
 * @param name the brand name
 */
public record CarBrandDto(UUID id, String name) {}
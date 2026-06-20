package com.carsocialmedia.backend.garage.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A single dream car within a {@link DreamCarRequest}, and the body of a single-item update.
 *
 * @param brandId the dreamed-of brand (required)
 * @param modelId the specific model (optional; when present it must belong to the brand)
 */
public record DreamCarRequestBody(
        @NotNull UUID brandId,
        UUID modelId
) {}
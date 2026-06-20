package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's dream car. Brand/model are denormalized (id + name) so the client has both
 * form state and display labels. {@code modelId}/{@code modelName} are null when the user
 * dreams of a brand without picking a specific model.
 *
 * @param id        the dream car ID
 * @param brandId   the brand ID
 * @param brandName the brand name
 * @param modelId   the model ID (nullable)
 * @param modelName the model name (nullable)
 * @param createdAt when it was added
 */
public record DreamCarDto(
        UUID id,
        UUID brandId,
        String brandName,
        UUID modelId,
        String modelName,
        Instant createdAt
) {}
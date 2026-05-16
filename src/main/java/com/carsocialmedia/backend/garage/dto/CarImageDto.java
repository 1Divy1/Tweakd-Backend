package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A single gallery image for a car.
 *
 * @param id the gallery image ID
 * @param storagePath the object path inside the storage bucket
 * @param displayOrder ascending sort order within the car's gallery
 * @param createdAt when the image record was created
 */
public record CarImageDto(
        UUID id,
        String storagePath,
        int displayOrder,
        Instant createdAt
) {}
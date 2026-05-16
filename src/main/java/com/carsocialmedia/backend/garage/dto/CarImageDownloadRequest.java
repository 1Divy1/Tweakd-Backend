package com.carsocialmedia.backend.garage.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for a presigned download URL for a previously issued car photo path.
 *
 * @param storagePath the canonical {@code car-photos/{ownerId}/{carId}/...} object path
 */
public record CarImageDownloadRequest(
        @NotBlank String storagePath
) {}

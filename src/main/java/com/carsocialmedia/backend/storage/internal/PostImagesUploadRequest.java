package com.carsocialmedia.backend.storage.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Batch request for post-image presigned upload URLs.
 *
 * Post images are always webp, so the client only needs to say how many slots it wants; the
 * backend mints one presigned PUT URL per image and returns them all in one response.
 *
 * @param count how many upload URLs to generate (1–10)
 */
public record PostImagesUploadRequest(
        @Min(1) @Max(10) int count
) {}
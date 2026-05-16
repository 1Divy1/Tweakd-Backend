package com.carsocialmedia.backend.garage.dto;

import java.util.List;

/**
 * Result of the single-shot "add car" submission.
 *
 * The car and its modifications already exist in the DB with their final, deterministic
 * storage paths recorded. The client must now PUT each photo to its presigned URL. If any
 * upload fails, the client rolls back by calling {@code DELETE /api/v1/garage/cars/{carId}}
 * (modifications and gallery rows cascade).
 *
 * @param car the created car, with all references resolved and modifications embedded
 * @param cover upload slot for the car cover image
 * @param modifications per-modification before/after upload slots, in request order
 * @param gallery upload slots for the pre-allocated gallery images
 */
public record CreateCarResponse(
        CarDto car,
        UploadSlot cover,
        List<ModificationUploadSlots> modifications,
        List<GallerySlot> gallery
) {}

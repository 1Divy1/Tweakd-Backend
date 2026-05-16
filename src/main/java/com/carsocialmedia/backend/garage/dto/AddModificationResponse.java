package com.carsocialmedia.backend.garage.dto;

/**
 * Result of adding a modification to an existing car.
 *
 * The modification already exists in the DB with its final, deterministic storage paths
 * recorded. The client must PUT each photo to its presigned URL. If uploads fail, the
 * client rolls back by calling {@code DELETE /api/v1/garage/cars/{carId}/modifications/{modificationId}}.
 *
 * @param modification the created modification with all references resolved
 * @param before upload slot for the before/original state image
 * @param after upload slot for the after/modified state image
 */
public record AddModificationResponse(
        CarModificationDto modification,
        UploadSlot before,
        UploadSlot after
) {}

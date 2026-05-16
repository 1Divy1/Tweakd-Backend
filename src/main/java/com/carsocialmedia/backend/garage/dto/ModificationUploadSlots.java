package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

/**
 * The before/after upload destinations for one created modification.
 *
 * @param modificationId the modification row the client should attach these uploads to
 * @param before upload slot for the before/original image
 * @param after upload slot for the after/modified image
 */
public record ModificationUploadSlots(
        UUID modificationId,
        UploadSlot before,
        UploadSlot after
) {}

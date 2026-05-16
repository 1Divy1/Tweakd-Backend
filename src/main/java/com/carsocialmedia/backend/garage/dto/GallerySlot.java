package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

/**
 * A presigned upload destination for one gallery image, paired with the DB row id
 * the client should associate the upload with.
 *
 * @param imageId the {@code car_images} row id created for this slot
 * @param path the canonical object path the backend recorded
 * @param uploadUrl the Supabase presigned URL the client PUTs the file bytes to
 */
public record GallerySlot(
        UUID imageId,
        String path,
        String uploadUrl
) {}

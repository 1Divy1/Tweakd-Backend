package com.carsocialmedia.backend.garage.dto;

/**
 * A single presigned upload destination.
 *
 * @param path the canonical object path the backend recorded for this image
 * @param uploadUrl the Supabase presigned URL the client PUTs the file bytes to
 */
public record UploadSlot(
        String path,
        String uploadUrl
) {}

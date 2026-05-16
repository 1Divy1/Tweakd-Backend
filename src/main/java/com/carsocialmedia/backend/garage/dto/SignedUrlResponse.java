package com.carsocialmedia.backend.garage.dto;

/**
 * A short-lived presigned URL.
 *
 * @param signedUrl the URL the client uses to download the object
 */
public record SignedUrlResponse(
        String signedUrl
) {}

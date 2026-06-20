package com.carsocialmedia.backend.garage.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for replacing a car's gallery images.
 *
 * The list represents the complete desired gallery state. The backend deletes all existing
 * gallery rows and inserts this list in order. An empty list clears the gallery.
 *
 * @param urls ordered list of public R2 image URLs (max 20)
 */
public record GalleryUrlsRequest(
        @NotNull @Size(max = 20) List<String> urls
) {}
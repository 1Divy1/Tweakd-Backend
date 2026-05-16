package com.carsocialmedia.backend.garage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Single-shot payload for the "add car" wizard.
 *
 * The client collects every screen locally and submits once. The backend creates the
 * car and all modifications in one transaction, then returns presigned upload URLs so
 * the client can PUT the photo bytes directly to storage. No file bytes travel in this
 * request.
 *
 * @param car the car specifications (no image fields — paths are backend-generated)
 * @param modifications the modifications to create alongside the car (may be empty)
 * @param galleryCount how many gallery image slots to pre-allocate upload URLs for
 */
public record CreateCarRequest(
        @NotNull @Valid CarRequest car,
        @NotNull @Valid List<CarModificationRequest> modifications,
        @PositiveOrZero @Max(50) int galleryCount
) {}

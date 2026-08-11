package com.carsocialmedia.backend.mapevents.dto;

/**
 * An event subcategory — reference data for the create screen and the map's category filter.
 *
 * @param id    stable slug (e.g. {@code car_meet}); safe to key marker icons off
 * @param label display label (e.g. {@code "Car meet"})
 */
public record MapEventCategoryDto(
        String id,
        String label
) {}

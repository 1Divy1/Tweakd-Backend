package com.carsocialmedia.backend.profile.dto;

/**
 * A car category a user can mark as a favorite (e.g. JDM, classic, off-road).
 *
 * @param id   the category ID
 * @param name the display name
 */
public record CarCategoryDto(String id, String name) {}
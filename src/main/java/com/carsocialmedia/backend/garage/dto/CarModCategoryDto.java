package com.carsocialmedia.backend.garage.dto;

/**
 * A category for car modifications/upgrades (e.g., suspension, engine, wheels).
 *
 * @param id the category ID (typically a string identifier)
 * @param modName the category name (e.g., "Suspension", "Engine", "Exterior")
 */
public record CarModCategoryDto(String id, String modName) {}
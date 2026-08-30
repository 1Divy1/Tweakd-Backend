package com.tweakdapp.backend.garage.dto;

/**
 * A paint or body color for a car.
 *
 * @param id the color ID (typically a string identifier)
 * @param name the color name (e.g., "Black", "Deep Blue Pearl")
 * @param colorCode the hex color code (e.g., "#000000", "#0055CC")
 */
public record CarColorDto(String id, String name, String colorCode) {}
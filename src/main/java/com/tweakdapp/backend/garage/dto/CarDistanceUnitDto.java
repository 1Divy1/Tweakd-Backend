package com.tweakdapp.backend.garage.dto;

/**
 * A distance unit for measuring mileage (e.g., kilometers, miles).
 *
 * @param id the distance unit ID (typically a string identifier)
 * @param name the unit name (e.g., "Kilometers", "Miles")
 */
public record CarDistanceUnitDto(String id, String name) {}
package com.carsocialmedia.backend.shared.geo;

/**
 * A selectable country.
 *
 * @param id   the country ID (e.g. ISO code like "RO")
 * @param name the country name
 */
public record CountryDto(String id, String name) {}
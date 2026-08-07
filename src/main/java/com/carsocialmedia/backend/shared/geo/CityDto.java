package com.carsocialmedia.backend.shared.geo;

/**
 * A selectable city, with its geographic centre exposed as plain coordinates.
 *
 * @param id        the city ID
 * @param name      the city name
 * @param region    the region/state the city belongs to
 * @param countryId the owning country's ID
 * @param lat       latitude of the city's location
 * @param lng       longitude of the city's location
 */
public record CityDto(
        String id,
        String name,
        String region,
        String countryId,
        Double lat,
        Double lng
) {}
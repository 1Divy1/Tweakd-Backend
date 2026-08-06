package com.carsocialmedia.backend.business.dto;

import java.util.UUID;

/**
 * A single business as rendered on the virtual map. Deliberately compact: the map may hold
 * hundreds of these at once, so it carries only what a Mapbox marker and its preview callout
 * need. Tapping a pin navigates to the business profile, fetched separately as a
 * {@link BusinessDto} by {@code id}.
 *
 * @param id            the business id — pass to {@code GET /api/v1/businesses/{id}} on tap
 * @param name          display name
 * @param typeId        business type id (e.g. {@code tuning_shop}); stable, safe for icon mapping
 * @param typeLabel     human-readable type label (e.g. {@code "Tuning shop"})
 * @param lat           latitude of the business location (WGS84)
 * @param lng           longitude of the business location (WGS84)
 * @param logoUrl       logo image URL, or {@code null} if the business has none
 * @param averageRating average review score
 * @param reviewCount   number of reviews the average is based on
 * @param isOpenNow     whether the business is open at request time, derived from its opening
 *                      hours in its own timezone
 * @param distanceKm    great-circle distance from the search centre, in kilometres
 */
public record BusinessMapPinDto(
        UUID id,
        String name,
        String typeId,
        String typeLabel,
        double lat,
        double lng,
        String logoUrl,
        double averageRating,
        int reviewCount,
        boolean isOpenNow,
        double distanceKm
) {}

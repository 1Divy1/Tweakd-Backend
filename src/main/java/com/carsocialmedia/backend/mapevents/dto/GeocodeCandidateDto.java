package com.carsocialmedia.backend.mapevents.dto;

/**
 * One hit from a structured forward-geocoding search (Mapbox Geocoding v6) — a candidate location
 * for the create-event location picker's dedicated address fields. The client shows all returned
 * candidates as a tappable list (not an auto-fly-to-first-hit); the user picks one, the map
 * recentres and zooms there, and the user then drops their own pin — so nothing here is persisted
 * as-is, only used to steer the map.
 *
 * @param lat         latitude, -90..90
 * @param lng         longitude, -180..180
 * @param placeName   a display string for the candidate: Mapbox's {@code full_address} when
 *                     present, else {@code place_formatted}, else just the {@code name}
 * @param featureType Mapbox's match granularity, e.g. {@code address}, {@code street},
 *                     {@code place}, {@code postcode}, {@code region}, {@code country} — lets the
 *                     picker show what kind of hit each candidate is
 * @param accuracy     positional accuracy for {@code address}-level hits — one of {@code rooftop},
 *                     {@code parcel}, {@code point}, {@code interpolated}, {@code approximate},
 *                     {@code intersection}; {@code null} for coarser feature types where Mapbox
 *                     does not report it
 */
public record GeocodeCandidateDto(
        double lat,
        double lng,
        String placeName,
        String featureType,
        String accuracy
) {}
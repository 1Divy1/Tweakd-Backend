package com.tweakdapp.backend.mapevents.dto.request;

/**
 * Structured forward-geocoding input for {@code GET /geocode} — the create-event location
 * picker's dedicated address fields, sent to Mapbox's Geocoding v6 "structured input" mode
 * instead of a free-text search string, for materially better match accuracy. Field names and
 * semantics mirror Mapbox's own structured input parameters exactly.
 *
 * <p>Every field is optional individually, but the query is meaningless with all of them blank —
 * {@link com.tweakdapp.backend.mapevents.MapEventsService#geocode} returns an empty list in
 * that case rather than calling Mapbox, same idea as a blank {@code q} used to.
 *
 * @param addressLine1 street number and name combined, e.g. {@code "1600 Pennsylvania Ave"}
 * @param addressNumber house/building number, when given separately from {@code street}
 * @param street       street name
 * @param block        sub-division used by some countries (e.g. Japan); rarely needed here
 * @param place        city, village or municipality
 * @param region       state or province
 * @param postcode     postal code
 * @param locality     sub-city administrative area
 * @param neighborhood colloquial sub-city area
 * @param country      ISO 3166-1 alpha-2 code or full country name
 */
public record GeocodeQuery(
        String addressLine1,
        String addressNumber,
        String street,
        String block,
        String place,
        String region,
        String postcode,
        String locality,
        String neighborhood,
        String country
) {
    /** True when every field is null or blank — nothing worth sending to Mapbox. */
    public boolean isBlank() {
        return isBlank(addressLine1) && isBlank(addressNumber) && isBlank(street) && isBlank(block)
                && isBlank(place) && isBlank(region) && isBlank(postcode) && isBlank(locality)
                && isBlank(neighborhood) && isBlank(country);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

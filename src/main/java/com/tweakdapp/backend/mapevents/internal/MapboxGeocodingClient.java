package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.dto.request.GeocodeQuery;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.List;

/**
 * Mapbox's Geocoding v6 forward endpoint, called server-side so the access token never reaches
 * the Flutter client and request volume stays under this backend's control. Backs
 * {@link MapEventsServiceImpl#geocode}.
 *
 * <p>Always sent as a <a href="https://docs.mapbox.com/api/search/geocoding/#structured-input">
 * structured input</a> query (separate address component params, {@code autocomplete=false}) —
 * v6's more accurate mode over a free-text string — and always {@code permanent=false}: the
 * candidate a user taps only recentres the map, it is never itself written to the database. The
 * event's eventual {@code location} is a pin the user drops afterwards, which is their own input,
 * not Mapbox's.
 */
@Component
class MapboxGeocodingClient {

    /** Mapbox allows up to 10; the picker shows all of them, so 5 keeps the list short and cheap. */
    private static final int MAX_RESULTS = 5;

    private final RestClient restClient;
    private final String accessToken;

    MapboxGeocodingClient(@Value("${mapbox.access-token}") String accessToken) {
        this.accessToken = accessToken;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.mapbox.com/search/geocode/v6")
                .build();
    }

    List<MapboxFeature> forwardGeocode(GeocodeQuery query, Double proximityLat, Double proximityLng) {
        MapboxGeocodeResponse response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/forward")
                            .queryParam("access_token", accessToken)
                            .queryParam("permanent", false)
                            .queryParam("autocomplete", false)
                            .queryParam("limit", MAX_RESULTS);
                    addIfPresent(uriBuilder, "address_line1", query.addressLine1());
                    addIfPresent(uriBuilder, "address_number", query.addressNumber());
                    addIfPresent(uriBuilder, "street", query.street());
                    addIfPresent(uriBuilder, "block", query.block());
                    addIfPresent(uriBuilder, "place", query.place());
                    addIfPresent(uriBuilder, "region", query.region());
                    addIfPresent(uriBuilder, "postcode", query.postcode());
                    addIfPresent(uriBuilder, "locality", query.locality());
                    addIfPresent(uriBuilder, "neighborhood", query.neighborhood());
                    addIfPresent(uriBuilder, "country", query.country());
                    if (proximityLat != null && proximityLng != null) {
                        uriBuilder.queryParam("proximity", proximityLng + "," + proximityLat);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(MapboxGeocodeResponse.class);

        return response == null || response.features() == null ? List.of() : response.features();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapboxFeature(MapboxGeometry geometry, MapboxProperties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapboxGeometry(List<Double> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapboxProperties(
            @JsonProperty("full_address") String fullAddress,
            @JsonProperty("place_formatted") String placeFormatted,
            String name,
            @JsonProperty("feature_type") String featureType,
            MapboxCoordinates coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapboxCoordinates(String accuracy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MapboxGeocodeResponse(List<MapboxFeature> features) {
    }

    // ----- HELPERS -----
    private static void addIfPresent(UriBuilder uriBuilder, String name, String value) {
        if (value != null && !value.isBlank()) {
            uriBuilder.queryParam(name, value.trim());
        }
    }
}

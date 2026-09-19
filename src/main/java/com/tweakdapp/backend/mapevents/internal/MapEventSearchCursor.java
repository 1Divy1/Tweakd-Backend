package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for the map search, keyed on {@code (distance from the centre, id)}. Unlike
 * {@link MapEventCursor} the sort key is a distance, so it is only meaningful against the
 * <em>same</em> centre: the client must send the same {@code lat}/{@code lng} (and status filter)
 * with every page of one search.
 *
 * <p>The token is URL-safe Base64 of {@code "<distanceMetres>:<uuid>"}. {@link Double#toString}
 * round-trips exactly through {@link Double#parseDouble}, which the keyset's equality branch relies
 * on.
 */
record MapEventSearchCursor(double distanceMetres, UUID id) {

    /** The "first page" position: every real distance is {@code >= 0}, so all rows come after it. */
    static final MapEventSearchCursor START = new MapEventSearchCursor(-1, new UUID(0, 0));

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = Double.toString(distanceMetres) + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client token, or returns {@link #START} for a null/blank token ("first page").
     *
     * @throws InvalidCursorException if a non-blank token cannot be parsed
     */
    static MapEventSearchCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return START;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            double distance = Double.parseDouble(parts[0]);
            if (!Double.isFinite(distance) || distance < 0) {
                throw new IllegalArgumentException("distance out of range");
            }
            return new MapEventSearchCursor(distance, UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(token);
        }
    }
}

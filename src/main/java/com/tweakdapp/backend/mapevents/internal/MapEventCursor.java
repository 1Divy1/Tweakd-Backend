package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for this module's paged reads — the caller's own events, the admin review
 * queue, the attendee list and the car line-up. All four are keyed on {@code created_at}; the
 * trailing {@code id} tiebreaker (a profile id, car id or event id, depending on the list) makes
 * the ordering total so no row can be skipped or repeated across pages.
 *
 * <p>The token is URL-safe Base64 of {@code "<epochSecond>:<nano>:<uuid>"}, preserving full
 * timestamp precision (Postgres {@code timestamptz} is microsecond-precise). It is opaque — clients
 * just echo it back on the {@code ?cursor=} of the same list it came from.
 */
record MapEventCursor(Instant timestamp, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = timestamp.getEpochSecond() + ":" + timestamp.getNano() + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client token, or returns {@code null} for a null/blank token ("first page").
     *
     * @throws InvalidCursorException if a non-blank token cannot be parsed
     */
    static MapEventCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant ts = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new MapEventCursor(ts, UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(token);
        }
    }
}

package com.tweakdapp.backend.forums.internal;

import com.tweakdapp.backend.forums.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for the timestamp-keyed sorts — the <em>new</em> sort (keyed on
 * {@code created_at}), the <em>active</em> sort (keyed on {@code last_activity_at}), and reply
 * pagination (keyed on {@code created_at}). Carries the {@code (timestamp, id)} of the last row of
 * the page just returned; the {@code id} tiebreaker makes the ordering total.
 *
 * <p>The token is URL-safe Base64 of {@code "<epochSecond>:<nano>:<uuid>"}, preserving full
 * timestamp precision (Postgres {@code timestamptz} is microsecond-precise). Opaque — clients just
 * echo it. The cursor does not encode <em>which</em> timestamp column it keys; the caller always
 * pairs it with the same {@code ?sort=} it was issued for.
 */
record TimeCursor(Instant timestamp, UUID id) {

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
    static TimeCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant ts = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new TimeCursor(ts, UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(token);
        }
    }
}

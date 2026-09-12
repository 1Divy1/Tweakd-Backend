package com.tweakdapp.backend.business.internal;

import com.tweakdapp.backend.business.exception.InvalidBusinessCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for the admin review queue, keyed on {@code (created_at, id)} — the same
 * shape and reasoning as {@code MapEventCursor}: the trailing id makes the ordering total, so no
 * business can be skipped or repeated across pages when two rows share a timestamp.
 *
 * <p>The token is URL-safe Base64 of {@code "<epochSecond>:<nano>:<uuid>"}, preserving the full
 * microsecond precision of a Postgres {@code timestamptz}.
 */
record BusinessCursor(Instant timestamp, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = timestamp.getEpochSecond() + ":" + timestamp.getNano() + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a client token; {@code null} for a null/blank token ("first page"). */
    static BusinessCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant ts = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new BusinessCursor(ts, UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidBusinessCursorException(token);
        }
    }
}

package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.admin.exception.InvalidCaseCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque keyset cursor for the moderation queue, ordered by {@code (last_reported_at, id)} desc.
 * URL-safe Base64 of {@code "<epochSecond>:<nano>:<caseId>"}, preserving full timestamptz
 * precision; clients just echo it.
 */
record CaseCursor(Instant lastReportedAt, long id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = lastReportedAt.getEpochSecond() + ":" + lastReportedAt.getNano() + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client token, or returns {@code null} for a null/blank token ("first page").
     *
     * @throws InvalidCaseCursorException if a non-blank token cannot be parsed
     */
    static CaseCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant ts = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new CaseCursor(ts, Long.parseLong(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidCaseCursorException();
        }
    }
}

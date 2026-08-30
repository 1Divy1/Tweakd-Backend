package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.posts.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset-pagination cursor: the {@code (createdAt, id)} pair of the last row of
 * the page just returned.
 *
 * <p>Pages are ordered by {@code created_at DESC, id DESC}. The next page resumes
 * <em>strictly after</em> this cursor — i.e. rows whose {@code created_at} is older, or
 * equal-timestamp rows whose {@code id} sorts lower. Including {@code id} as a tiebreaker
 * makes the ordering total, so rows sharing a timestamp are never skipped or repeated.
 *
 * <p>The encoded token is URL-safe Base64 of {@code "<epochSecond>:<nano>:<uuid>"}. Full
 * timestamp precision is preserved (Postgres {@code timestamptz} is microsecond-precise),
 * and the token is opaque so clients treat it as a black box and just echo it back.
 */
record PageCursor(Instant createdAt, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = createdAt.getEpochSecond() + ":" + createdAt.getNano() + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client-supplied token, or returns {@code null} for a null/blank token
     * (meaning "first page").
     *
     * @throws InvalidCursorException if a non-blank token cannot be parsed
     */
    static PageCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new PageCursor(createdAt, UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(token);
        }
    }
}

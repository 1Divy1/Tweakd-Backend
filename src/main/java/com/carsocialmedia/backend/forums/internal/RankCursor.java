package com.carsocialmedia.backend.forums.internal;

import com.carsocialmedia.backend.forums.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for the <em>hot</em> sort: the {@code (rankingScore, id)} of the last row of
 * the page just returned. Pages are ordered {@code ranking_score DESC, id DESC}; the next page
 * resumes strictly after this cursor. The {@code id} tiebreaker makes the ordering total so
 * equal-score rows are never skipped or repeated in a single pass.
 *
 * <p>The token is URL-safe Base64 of {@code "<score-bits>:<uuid>"}, with the score stored as its raw
 * {@code long} bit pattern so full precision survives the round-trip. Opaque — clients just echo it.
 */
record RankCursor(double rankingScore, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = Double.doubleToLongBits(rankingScore) + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client token, or returns {@code null} for a null/blank token ("first page").
     *
     * @throws InvalidCursorException if a non-blank token cannot be parsed
     */
    static RankCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            double score = Double.longBitsToDouble(Long.parseLong(parts[0]));
            return new RankCursor(score, UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(token);
        }
    }
}

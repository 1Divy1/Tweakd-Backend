package com.carsocialmedia.backend.posts.internal;

import com.carsocialmedia.backend.posts.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset-pagination cursor for the global feed: the {@code (rankingScore, id)} pair of the
 * last row of the page just returned.
 *
 * <p>Pages are ordered by {@code ranking_score DESC, id DESC}. The next page resumes
 * <em>strictly after</em> this cursor — i.e. rows with a lower score, or equal-score rows whose
 * {@code id} sorts lower. Including {@code id} as a tiebreaker makes the ordering total so rows
 * sharing a score are never skipped or repeated within a single pass.
 *
 * <p>Unlike {@link PageCursor} (which keys on a stable {@code created_at}), the ranking score is
 * mutable, so the global feed is only eventually consistent across pages — see
 * {@code PostRepository#findRankedPostPage}.
 *
 * <p>The encoded token is URL-safe Base64 of {@code "<score-bits>:<uuid>"}, where the score is
 * stored as its raw {@code long} bit pattern ({@link Double#doubleToLongBits}) so full precision
 * survives the round-trip. The token is opaque; clients just echo it back.
 */
record RankCursor(double rankingScore, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = Double.doubleToLongBits(rankingScore) + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client-supplied token, or returns {@code null} for a null/blank token
     * (meaning "first page").
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

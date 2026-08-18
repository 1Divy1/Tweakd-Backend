package com.carsocialmedia.backend.feedbackfeed.internal;

import com.carsocialmedia.backend.feedbackfeed.exception.InvalidFeedbackFeedCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for this module's paged reads. Two shapes, because the feed's sorts are
 * keyed on different columns:
 *
 * <ul>
 *   <li><strong>time</strong> — {@code created_at} (newest / oldest / the dashboard list) or
 *       {@code completed_at} (the completed section)</li>
 *   <li><strong>score</strong> — {@code net_votes}, for the "popular" sort</li>
 * </ul>
 *
 * <p>Both carry a trailing message id, making the ordering total so no row can be skipped or
 * repeated across pages. The encoded token names its own shape ({@code t:} / {@code s:}), so
 * replaying a "popular" cursor against a time-ordered feed is rejected rather than quietly decoded
 * into a bogus timestamp.
 *
 * <p>The token is URL-safe Base64 of {@code "t:<epochSecond>:<nano>:<uuid>"} or
 * {@code "s:<netVotes>:<uuid>"}, preserving full timestamp precision (Postgres {@code timestamptz}
 * is microsecond-precise). Clients just echo it back on the {@code ?cursor=} of the same list it
 * came from.
 */
record FeedbackFeedCursor(Instant timestamp, Integer score, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    static FeedbackFeedCursor byTime(Instant timestamp, UUID id) {
        return new FeedbackFeedCursor(timestamp, null, id);
    }

    static FeedbackFeedCursor byScore(int netVotes, UUID id) {
        return new FeedbackFeedCursor(null, netVotes, id);
    }

    String encode() {
        String raw = timestamp != null
                ? "t:" + timestamp.getEpochSecond() + ":" + timestamp.getNano() + ":" + id
                : "s:" + score + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client token, or returns {@code null} for a null/blank token ("first page").
     *
     * @param scored whether the list being paged is sorted by score; a token of the other shape is
     *               rejected
     * @throws InvalidFeedbackFeedCursorException if a non-blank token cannot be parsed, or was
     *                                            issued for a differently sorted list
     */
    static FeedbackFeedCursor decode(String token, boolean scored) {
        if (token == null || token.isBlank()) {
            return null;
        }
        FeedbackFeedCursor cursor = parse(token);
        if (scored != (cursor.score() != null)) {
            throw new InvalidFeedbackFeedCursorException(token);
        }
        return cursor;
    }

    private static FeedbackFeedCursor parse(String token) {
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 4);
            return switch (parts[0]) {
                case "t" -> byTime(
                        Instant.ofEpochSecond(Long.parseLong(parts[1]), Long.parseLong(parts[2])),
                        UUID.fromString(parts[3]));
                case "s" -> byScore(Integer.parseInt(parts[1]), UUID.fromString(parts[2]));
                default -> throw new InvalidFeedbackFeedCursorException(token);
            };
        } catch (RuntimeException e) {
            throw new InvalidFeedbackFeedCursorException(token);
        }
    }
}

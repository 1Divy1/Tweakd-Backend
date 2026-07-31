package com.carsocialmedia.backend.tags.internal;

import com.carsocialmedia.backend.tags.exception.InvalidTagCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for the merged tags feed: the {@code (taggedAt, targetId)} pair of the last
 * item of the page just returned.
 *
 * <p>One cursor covers all four tag streams. Each stream re-applies it independently — every stream
 * is ordered by the same {@code (taggedAt, id)} key, so resuming each of them strictly after the
 * same point and re-merging yields exactly the items that follow, whichever stream they come from.
 *
 * <p>The encoded token is URL-safe Base64 of {@code "<epochSecond>:<nano>:<uuid>"}, matching the
 * cursor scheme the posts and forums modules already use.
 */
record TagCursor(Instant taggedAt, UUID targetId) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String raw = taggedAt.getEpochSecond() + ":" + taggedAt.getNano() + ":" + targetId;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a client-supplied token, or returns {@code null} for a null/blank token (meaning
     * "first page").
     *
     * @throws InvalidTagCursorException if a non-blank token cannot be parsed
     */
    static TagCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant taggedAt = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new TagCursor(taggedAt, UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidTagCursorException(token);
        }
    }
}

package com.tweakdapp.backend.feedback.internal;

import com.tweakdapp.backend.feedback.exception.InvalidFeedbackCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Keyset cursor for the feedback board and its comments: base64url of {@code "<key>|<uuid>"}, where
 * the key is the active sort's last value — an ISO instant for time sorts, a decimal count for
 * vote-based sorts. The key stays a string until the caller asks for the typed view, so one cursor
 * class serves every sort.
 *
 * @param key the raw sort-key half of the token
 * @param id the tiebreaker row id
 */
record BoardCursor(String key, UUID id) {

    static String encode(String key, UUID id) {
        String raw = key + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidFeedbackCursorException if the token cannot be parsed */
    static BoardCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            return new BoardCursor(raw.substring(0, separator), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new InvalidFeedbackCursorException(token);
        }
    }

    /** The key as an instant (new-sort and comment cursors). */
    Instant instantKey() {
        try {
            return Instant.parse(key);
        } catch (RuntimeException e) {
            throw new InvalidFeedbackCursorException(key);
        }
    }

    /** The key as a count (top / trending cursors). */
    long longKey() {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new InvalidFeedbackCursorException(key);
        }
    }
}

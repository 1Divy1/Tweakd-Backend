package com.carsocialmedia.backend.support.internal;

import com.carsocialmedia.backend.support.exception.InvalidTicketCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Keyset cursor over (last_message_at, id) desc for the ticket lists: base64url of
 * {@code "<ISO instant>|<uuid>"}.
 *
 * @param lastMessageAt the previous page's last activity timestamp
 * @param id the tiebreaker ticket id
 */
record TicketCursor(Instant lastMessageAt, UUID id) {

    String encode() {
        String raw = lastMessageAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidTicketCursorException if the token cannot be parsed */
    static TicketCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            return new TicketCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new InvalidTicketCursorException(token);
        }
    }
}

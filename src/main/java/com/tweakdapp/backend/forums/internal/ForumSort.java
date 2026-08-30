package com.tweakdapp.backend.forums.internal;

import com.tweakdapp.backend.forums.exception.InvalidSortException;

/**
 * The three thread sorts every list supports. {@code HOT} orders by the Reddit-style
 * {@code ranking_score}; {@code NEW} by {@code created_at}; {@code ACTIVE} by
 * {@code last_activity_at}. Each carries a different cursor key (see the service).
 */
enum ForumSort {

    HOT,
    NEW,
    ACTIVE;

    /**
     * Parses the {@code ?sort=} query param, tolerating case and null (null/blank defaults to
     * {@link #HOT}). Any other unrecognized value fails loudly so client typos surface during
     * development instead of silently returning the wrong ordering.
     *
     * @throws InvalidSortException if a non-blank value is not one of {@code hot}/{@code new}/{@code active}
     */
    static ForumSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return HOT;
        }
        return switch (raw.trim().toLowerCase()) {
            case "hot" -> HOT;
            case "new" -> NEW;
            case "active" -> ACTIVE;
            default -> throw new InvalidSortException(raw);
        };
    }
}

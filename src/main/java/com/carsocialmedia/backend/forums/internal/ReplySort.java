package com.carsocialmedia.backend.forums.internal;

import com.carsocialmedia.backend.forums.exception.InvalidSortException;

/**
 * The two orders a reply list supports. {@code OLD} (default) reads a thread top-to-bottom, oldest
 * first; {@code NEW} surfaces the most recent replies first. Both keyset-paginate on
 * {@code (created_at, id)} via {@link TimeCursor} — only the comparison direction differs. There is
 * deliberately no "hot" reply sort (replies carry no ranking score).
 */
enum ReplySort {

    OLD,
    NEW;

    /**
     * Parses the reply {@code ?sort=} query param, tolerating case and null (null/blank defaults to
     * {@link #OLD}). Any other value fails loudly with a 400 so client typos surface early.
     *
     * @throws InvalidSortException if a non-blank value is not {@code old} or {@code new}
     */
    static ReplySort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OLD;
        }
        return switch (raw.trim().toLowerCase()) {
            case "old" -> OLD;
            case "new" -> NEW;
            default -> throw new InvalidSortException(raw, "old, new");
        };
    }
}

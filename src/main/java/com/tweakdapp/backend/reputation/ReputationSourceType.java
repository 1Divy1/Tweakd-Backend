package com.tweakdapp.backend.reputation;

/**
 * The kinds of thing a reputation entry can point at.
 *
 * <p>Mirrors the {@code reputation_history_source_type_valid} check constraint. These are
 * deliberately <em>not</em> foreign keys to the tables they name — a history entry has to outlive
 * the row that earned it, and reputation is sourced from too many different tables for one FK
 * column each. See the source-link migration for the full reasoning.
 *
 * <p>Adding a kind means adding it here <em>and</em> to the check constraint; the database is the
 * one that will actually stop a typo.
 */
public final class ReputationSourceType {

    private ReputationSourceType() {
    }

    /** {@code car_events.id} — attendance, showcasing, organizing, no-shows. */
    public static final String CAR_EVENT = "car_event";

    /** A contest row, once contests exist as their own table. */
    public static final String CONTEST = "contest";

    /** {@code cars.id}. */
    public static final String CAR = "car";

    /** {@code car_modifications.id} — the first mod on a car. */
    public static final String CAR_MODIFICATION = "car_modification";

    /** {@code forum_threads.id}. */
    public static final String FORUM_THREAD = "forum_thread";

    /** {@code forum_thread_replies.id}. */
    public static final String FORUM_REPLY = "forum_reply";

    /** A marketplace listing, once the marketplace exists. */
    public static final String MARKETPLACE_LISTING = "marketplace_listing";

    /** A buyer/seller review, once reviews exist. */
    public static final String REVIEW = "review";
}

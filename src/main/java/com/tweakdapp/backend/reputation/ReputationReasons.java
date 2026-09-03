package com.tweakdapp.backend.reputation;

/**
 * The seeded {@code reputation_score_reason_options} codes, as constants.
 *
 * <p>Deliberately constants rather than an enum: the catalogue is reference data owned by the
 * database, so it can grow a row without a deploy, and an enum would turn every such row into a
 * compile-time obligation. These exist purely so the modules that award reputation today do not
 * pass string literals around and typo one — an unknown code is still caught at award time by
 * {@link ReputationService#award(java.util.UUID, String)}, which resolves it against the table.
 *
 * <p>Kept in step with the seeded rows by hand. A constant here whose row has been renamed or
 * retired fails loudly on the first award, not silently.
 */
public final class ReputationReasons {

    private ReputationReasons() {
    }

    // ---- events -------------------------------------------------------------

    /** Checked in at a community car event. Source: {@code car_event}. */
    public static final String EVENT_ATTENDED = "event_attended";

    /** Brought and presented a car at an event. Source: {@code car_event}. */
    public static final String EVENT_CAR_SHOWCASED = "event_car_showcased";

    /** Organized an event that was approved and took place. Source: {@code car_event}. */
    public static final String CAR_EVENT_ORGANIZED = "car_event_organized";

    // ---- contests -----------------------------------------------------------

    /** Source: {@code contest}. */
    public static final String CONTEST_FIRST_PLACE = "contest_first_place";

    /** Source: {@code contest}. */
    public static final String CONTEST_SECOND_PLACE = "contest_second_place";

    /** Source: {@code contest}. */
    public static final String CONTEST_THIRD_PLACE = "contest_third_place";

    // ---- garage -------------------------------------------------------------

    /**
     * The first modification logged on a car. Source: {@code car} — scoping it to the car, not the
     * modification, is what makes it "first": the unique index then pays it once per car.
     */
    public static final String FIRST_MOD = "first_mod";

    // ---- trust --------------------------------------------------------------

    /** Another full year in the community. No source — see {@link ReputationService#award}. */
    public static final String ANNUAL_MEMBER_ANNIVERSARY = "annual_member_anniversary";

    // ---- moderation (negative points) ---------------------------------------

    /** Marked as attending and never showed up, without telling the organizers. Source: {@code car_event}. */
    public static final String EVENT_NO_SHOW = "event_no_show";
}

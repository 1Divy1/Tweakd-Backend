package com.tweakdapp.backend.badges;

/**
 * The badge codes the backend awards by name, so a typo is a compile error rather than a badge that
 * silently never unlocks.
 *
 * <p>Deliberately short, and it should stay that way. The ordinary way a badge is unlocked is
 * {@link BadgeService#awardForTrigger}: a module reports that something happened and the
 * {@code badges} table says which badges that is worth, so a new badge needs a row rather than a
 * constant. A code belongs here only when some call site genuinely has to name that one badge.
 *
 * <p>A code listed here must exist in the {@code badges} table; {@code award} refuses an unknown
 * one, a retired one, or one whose offer window has closed.
 */
public final class Badges {

    /**
     * Early community member: the account was created during the launch year.
     *
     * <p>Awarded automatically, but <strong>not by name</strong> — nothing in the backend calls
     * {@code award(user, PIONEER)}. The badge row carries
     * {@code award_trigger = 'account_created'} and an offer window, and {@code profile} reports
     * {@link BadgeTrigger#ACCOUNT_CREATED} when a new member finishes onboarding; the catalogue
     * decides that this is what the event is currently worth. Retiring it, extending its year, or
     * replacing it with a successor badge is therefore an edit on the dashboard, not a deploy.
     *
     * <p>Which is also why this constant is not used anywhere. It stays because the code is a fixed
     * string that a hand-grant, a migration or a test may need to name.
     */
    public static final String PIONEER = "pioneer";

    private Badges() {}
}

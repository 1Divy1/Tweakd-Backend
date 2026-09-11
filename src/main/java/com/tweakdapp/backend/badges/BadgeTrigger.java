package com.tweakdapp.backend.badges;

import java.util.Optional;

/**
 * The events a sibling module reports to this one, so that badges can be unlocked without the
 * reporting module knowing which badges exist.
 *
 * <p>This is the whole extensibility story. A module that witnesses something calls
 * {@link BadgeService#awardForTrigger} with the trigger and says nothing about badges; which badges
 * a trigger unlocks — and for how long it is offered — lives in the {@code badges} table. Adding a
 * second badge for an event that is already reported is a row in the dashboard. Only a genuinely
 * new event needs a constant here.
 *
 * <p>A trigger says <strong>an event happened</strong>, not that a condition is met. The reporting
 * module decides whether its own domain rule fired (that this really was the user's first car, say)
 * and calls only then; this module decides what that is worth. That split is what keeps
 * {@code badges} free of every other module's domain logic, and therefore importable by all of
 * them — the same constraint that keeps it from reading {@code profiles}.
 *
 * <p>{@link #code()} is the value stored in {@code badges.award_trigger}, and the database has a
 * CHECK constraint listing the same set. Adding a constant here therefore means a migration
 * extending {@code badges_award_trigger_known_ck} in the same release. That is not extra friction:
 * a new trigger already needs a deploy for the call site that fires it, and the constraint is what
 * turns a mistyped trigger into a rejected write rather than a badge that silently stops
 * unlocking.
 */
public enum BadgeTrigger {

    /**
     * An account became a real member — a new user finished onboarding.
     *
     * <p>Reported by {@code profile}, once, from inside the onboarding transaction. The moment
     * judged against a badge's window is the <strong>account's creation date</strong>, not the
     * moment of onboarding: "you were here early" is a fact about signing up, and someone who
     * signed up two days before a cutoff should not lose the badge for finishing onboarding a week
     * later.
     *
     * <p>Not fired at signup itself, because signup happens in Supabase — the {@code handle_new_user}
     * trigger on {@code auth.users} inserts the profile, and the backend never sees it. Onboarding
     * is the first moment the backend can act on a new account, and it is also the first moment
     * there is a member rather than an abandoned signup.
     */
    ACCOUNT_CREATED("account_created"),

    /**
     * A car took <strong>first place</strong> in a contest run inside a car event.
     *
     * <p>Reported by {@code mapevents} from inside the transaction that finalises the contest,
     * for the winning car's owner. The moment judged against a badge's window is the contest's
     * {@code finished_at}. Only placements that received at least one vote are reported.
     */
    CONTEST_FIRST_PLACE("contest_first_place"),

    /** A car took <strong>second place</strong>. See {@link #CONTEST_FIRST_PLACE}. */
    CONTEST_SECOND_PLACE("contest_second_place"),

    /** A car took <strong>third place</strong>. See {@link #CONTEST_FIRST_PLACE}. */
    CONTEST_THIRD_PLACE("contest_third_place");

    private final String code;

    BadgeTrigger(String code) {
        this.code = code;
    }

    /** The value stored in {@code badges.award_trigger}. */
    public String code() {
        return code;
    }

    /**
     * Resolves a stored or submitted trigger code.
     *
     * <p>Empty for anything unknown — including {@code null} and blank, which both mean "no trigger;
     * this badge is hand-granted". Callers decide what to do with that: the admin write path
     * rejects it so a typo cannot be saved, while a read treats it as a badge no event unlocks.
     */
    public static Optional<BadgeTrigger> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalised = code.strip().toLowerCase();
        for (BadgeTrigger trigger : values()) {
            if (trigger.code.equals(normalised)) {
                return Optional.of(trigger);
            }
        }
        return Optional.empty();
    }
}

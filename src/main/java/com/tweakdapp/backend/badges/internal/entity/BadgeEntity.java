package com.tweakdapp.backend.badges.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One unlockable badge.
 *
 * <p>Reference data ({@code badges}), curated from the admin dashboard. {@code user_badges.badge_id}
 * is a foreign key to {@link #id} with {@code ON DELETE RESTRICT}, so a badge someone has earned
 * cannot be deleted — it is withdrawn by clearing {@link #available} instead, which stops new
 * awards while leaving the profiles that hold it alone.
 *
 * <p>The two url columns hold <strong>R2 object keys</strong>, not URLs
 * ({@code badges/pioneer/badge-unlocked.svg}). The full public URL is built at read time from the
 * configured {@code app-assets} bucket, so moving the bucket or its domain is a config change
 * rather than a data migration. A CHECK constraint refuses a value starting with {@code http},
 * because the failure mode otherwise is a silently doubled prefix and a broken image in the app.
 */
@Entity
@Table(name = "badges")
@Getter
@Setter
public class BadgeEntity {

    /** The stable code recorded on every unlock. Chosen once; renaming it cascades to holders. */
    @Id
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    /** What it takes to unlock the badge. Optional. */
    @Column(name = "description")
    private String description;

    /** R2 key of the earned artwork. */
    @Column(name = "unlocked_badge_url", nullable = false)
    private String unlockedKey;

    /** R2 key of the not-yet-earned artwork, or {@code null} if the badge has no locked variant. */
    @Column(name = "locked_badge_url")
    private String lockedKey;

    /**
     * Retired badges stay on the profiles that earned them but can no longer be awarded.
     *
     * <p>The manual half of awardability — a kill switch staff hold. {@link #earnableFrom} /
     * {@link #earnableUntil} are the automatic half; see {@link #isEarnableAt(Instant)}.
     */
    @Column(name = "is_available", nullable = false)
    private boolean available;

    /**
     * The event that unlocks this badge automatically, as a
     * {@link com.tweakdapp.backend.badges.BadgeTrigger#code() trigger code}, or {@code null} for a
     * badge only staff hand out.
     *
     * <p>Mapped as the raw string rather than as the enum on purpose. An {@code @Enumerated} or a
     * converter turns an unrecognised value into an exception thrown while <em>reading</em>, which
     * would mean one bad row breaking the badge catalogue for every user. As a string, an unknown
     * code matches no trigger and the badge simply stays hand-granted. It cannot get here anyway —
     * the admin write path validates against {@code BadgeTrigger} and the column has a CHECK
     * constraint — and this is the layer that has nothing to gain from finding out the hard way.
     */
    @Column(name = "award_trigger")
    private String awardTrigger;

    /** Start of the window in which the trigger pays out, inclusive. {@code null} = unbounded. */
    @Column(name = "earnable_from")
    private Instant earnableFrom;

    /**
     * End of the window in which the trigger pays out, exclusive. {@code null} = unbounded.
     *
     * <p>This is how a limited-time badge withdraws itself on its date without anybody remembering
     * to do it. Passing it stops new awards only; the profiles already holding the badge are
     * untouched, exactly as retiring it would be.
     */
    @Column(name = "earnable_until")
    private Instant earnableUntil;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * Whether this badge may be awarded for something that happened at {@code at} — availability
     * and the offer window together, which is what every automatic award path means by "awardable".
     *
     * <p>The window is half-open, {@code [earnableFrom, earnableUntil)}: a badge whose window ends
     * at midnight is earnable for something that happened at 23:59:59 and not for something that
     * happened at midnight exactly. Both bounds are optional and a {@code null} means unbounded, so
     * an ordinary permanent badge answers on {@link #available} alone.
     *
     * <p>{@code at} is the moment being judged — when the achievement happened — not necessarily
     * now. For {@code pioneer} it is the account's creation date, which is what stops a slow
     * onboarding from costing someone a badge they earned by signing up in time.
     *
     * <p>Mirrored by the {@code where} clauses in {@link
     * com.tweakdapp.backend.badges.internal.repository.BadgeRepository}, which apply the same test
     * in SQL so the database never returns a badge this would reject. Kept here as well because
     * this is the readable statement of the rule, and the one the single-badge paths use.
     */
    public boolean isEarnableAt(Instant at) {
        return available
                && (earnableFrom == null || !at.isBefore(earnableFrom))
                && (earnableUntil == null || at.isBefore(earnableUntil));
    }
}

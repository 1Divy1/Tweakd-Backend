package com.tweakdapp.backend.badges.dto;

import java.time.Instant;

/**
 * One badge from the catalogue, as the client renders it.
 *
 * <p>Both url fields are <strong>full public URLs</strong>, already built from the R2 keys the
 * database stores — the client fetches the SVG from them directly and never has to know about
 * buckets or key layout.
 *
 * @param id           the stable badge code, e.g. {@code pioneer}
 * @param title        display name, e.g. "Pioneer"
 * @param description  what it takes to unlock it; may be {@code null}
 * @param unlockedUrl  artwork for a badge the user holds
 * @param lockedUrl    artwork for one they don't. {@code null} when the badge has no locked
 *                     variant, in which case the client greys out {@code unlockedUrl} itself
 * @param available    {@code false} for a retired badge — never returned by the catalogue, but a
 *                     user who already holds one still sees it on their profile
 * @param awardTrigger the event that unlocks this badge automatically
 *                     ({@link com.tweakdapp.backend.badges.BadgeTrigger#code()}), or {@code null}
 *                     for one only staff hand out. Not a secret — nothing lets a user fire a
 *                     trigger for themselves — and the dashboard has to round-trip it on edit
 * @param earnableFrom start of the window in which it can be unlocked, inclusive; {@code null} =
 *                     unbounded
 * @param earnableUntil end of that window, exclusive; {@code null} = unbounded. A limited-time
 *                     badge carries a date here, which is what lets the app show "X days left"
 *                     rather than the badge simply vanishing from the catalogue one morning
 * @param createdAt    when the badge was added to the catalogue
 */
public record BadgeDto(
        String id,
        String title,
        String description,
        String unlockedUrl,
        String lockedUrl,
        boolean available,
        String awardTrigger,
        Instant earnableFrom,
        Instant earnableUntil,
        Instant createdAt
) {}

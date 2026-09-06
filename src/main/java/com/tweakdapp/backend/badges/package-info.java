/**
 * The Badges module owns the app's <strong>unlockable badges</strong>: the admin-curated catalogue
 * of what can be earned ({@code badges}), and which user has earned what ({@code user_badges}).
 *
 * <h2>How it differs from reputation</h2>
 * Reputation is a running total with an itemised ledger behind it; a badge is a single
 * <em>current-state</em> fact — you hold it or you don't. That difference drives everything here:
 * a badge has no points, no history rows, no revocation tombstone. Revoking deletes the row, and
 * the badge becomes earnable again. The two features are complementary and independent: an
 * achievement may award reputation, a badge, both, or neither.
 *
 * <h2>Where the data lives</h2>
 * <ul>
 *   <li>{@code badges} — reference data, admin-managed. The badge's {@code id} is a stable code
 *       ({@code pioneer}), the two url columns hold <strong>R2 object keys</strong>, not URLs, and
 *       {@code award_trigger} / {@code earnable_from} / {@code earnable_until} say what unlocks it
 *       and for how long it is offered.</li>
 *   <li>{@code user_badges} — one row per (user, badge), FK to {@code profiles} and {@code badges}.
 *       A unique index on the pair is what makes awarding idempotent.</li>
 * </ul>
 *
 * <h2>Artwork</h2>
 * Badge SVGs live in the Cloudflare R2 {@code app-assets} bucket. The database stores the key
 * ({@code badges/pioneer/badge-unlocked.svg}); this module turns it into a full public URL through
 * {@link com.tweakdapp.backend.storage.StorageService#publicUrl}, exactly as avatars and post
 * images do. Moving the bucket or its domain is then a config change, never a data migration.
 * Every badge carries an unlocked variant; the locked one is optional, and {@code null} means the
 * client greys the unlocked artwork out itself.
 *
 * <h2>Where badges are read</h2>
 * A user's earned badges are <strong>embedded in the profile response</strong> — {@code ProfileDto}
 * and {@code PublicProfileDto} each carry them — so the profile screen paints its badge row in the
 * same round trip as the header, the way Instagram highlights sit under the bio.
 *
 * <p>That is why this module reads nothing from {@code profiles}. {@code profile} depends on
 * {@code badges}; the reverse would be a cycle, and Modulith fails the build on one. Concretely:
 * there is no username-keyed read here, and {@code award} does not check that the profile exists
 * (the foreign key still does). The username-keyed refetch lives in {@code profile}, which is the
 * module that owns username resolution anyway.
 *
 * <p>The locked list — every available badge a user has <em>not</em> earned, with its locked
 * artwork and the description of how to get it — is the caller's own only, and stays a separate
 * read: it grows with the catalogue and is wanted only when that section is opened.
 *
 * <h2>Awarding</h2>
 * Two ways in, and no way for a user to award themselves:
 * <ul>
 *   <li><strong>The SPI</strong> — {@code awardForTrigger(userId, trigger, occurredAt)}, called
 *       in-process by the module that witnessed the event, from inside that event's own
 *       transaction. The caller names <em>no badge</em>: it reports what happened, and the
 *       {@code badges} table decides what that is worth. {@code award(userId, badgeId)} is the
 *       by-name variant, for a rule genuinely specific to one call site.</li>
 *   <li><strong>By hand</strong> — the admin dashboard, for badges that are a judgement call
 *       ("helped at the meet") rather than something a rule can detect. Those calls carry the
 *       granting staff member's id onto {@code user_badges.granted_by} — a staff-only audit trail,
 *       never returned by anything the app calls. A badge on a profile is the user's achievement;
 *       "granted by X" beside it would read as a favour rather than something earned.</li>
 * </ul>
 * The module's own controller is read-only.
 *
 * <h2>Triggers and offer windows: why a new badge is not a deploy</h2>
 * Two columns on {@code badges} carry the whole rule:
 * <ul>
 *   <li>{@code award_trigger} — the {@link com.tweakdapp.backend.badges.BadgeTrigger} whose event
 *       unlocks it, or {@code null} for a hand-granted badge. Because the reporting module names no
 *       badge, adding a second badge to an event already being reported is a row in this table.</li>
 *   <li>{@code earnable_from} / {@code earnable_until} — the half-open window
 *       {@code [from, until)} in which the trigger pays out. This is how a limited-time badge
 *       withdraws itself on its date, with nobody having to remember: {@code pioneer} is offered
 *       for the launch year and stops on its own.</li>
 * </ul>
 * A badge is awardable when it is {@code is_available} <em>and</em> inside its window. The window
 * is judged against <strong>the moment the achievement happened</strong>, not against now — for
 * {@code pioneer} that is the account's creation date, so signing up two days before the cutoff and
 * onboarding a week after it still earns the badge. Staff hand-grants ignore the window (a support
 * case is exactly what they are for) but still respect retirement.
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.tweakdapp.backend.badges.BadgeService}. Badge codes are available as
 * constants on {@link com.tweakdapp.backend.badges.Badges}.
 *
 * <h2>Cross-module dependencies</h2>
 * {@code storage} (public URLs) and {@code shared} (exception hierarchy) — deliberately not
 * {@code profile}, so that {@code profile} can depend on this module instead. {@code admin} depends
 * on it for the dashboard's badge administration.
 */
@ApplicationModule(
        displayName = "Badges"
)
package com.tweakdapp.backend.badges;

import org.springframework.modulith.ApplicationModule;

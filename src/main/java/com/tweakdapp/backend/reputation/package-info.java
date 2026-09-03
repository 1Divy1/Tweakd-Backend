/**
 * The Reputation module owns the app's <strong>community reputation score</strong>: a single number
 * on every profile that adds up everything a user has actually done in the community — attended and
 * organised events, showed cars, won contests, answered forum questions, kept a garage documented —
 * together with the dated, itemised history behind it.
 *
 * <h2>Why the history matters more than the number</h2>
 * The score alone is just a number; the history is the credibility. Every change writes a
 * {@code reputation_score_history} row carrying the reason, the delta, and the score before and
 * after, so a visitor can scroll a chronological record of how the total was built. That record is
 * what makes the score unfakeable: it costs time and turning up, not clicks. The future marketplace
 * reads it as a seller trust signal.
 *
 * <h2>Where the data lives</h2>
 * <ul>
 *   <li>{@code reputation_score_reason_options} — reference data, owned here. Every way to gain or lose points,
 *       with its default value, category and whether it may be earned more than once.</li>
 *   <li>{@code reputation_score_history} — owned here. One row per award, FK to the reason.</li>
 *   <li>{@code profiles.reputation_score} — owned by {@code profile}. This module never touches the
 *       column directly; it calls
 *       {@link com.tweakdapp.backend.profile.ProfileService#applyReputationDelta} and records the
 *       before/after pair that call returns.</li>
 * </ul>
 *
 * <h2>Consistency</h2>
 * There is deliberately <strong>no database trigger</strong> keeping the score and the history in
 * step. An award is one Spring transaction: the profile row is taken under a pessimistic write
 * lock, the score is moved, and the history row is written from the exact before/after the lock
 * produced. Either both land or neither does, and concurrent awards to the same user serialise
 * rather than losing an increment.
 *
 * <h2>Penalties</h2>
 * Reasons in the {@code moderation} category carry negative points. The resulting score is clamped
 * at zero, and the history row records the clamped delta — so the timeline never claims to have
 * removed more points than the user had.
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.tweakdapp.backend.reputation.ReputationService}
 * <ul>
 *   <li><strong>Awarding (the SPI):</strong> {@code award(userId, reasonId)} and the
 *       points-overriding {@code award(userId, reasonId, points)} — called by sibling modules
 *       ({@code mapevents}, {@code forums}, {@code garage}, later the marketplace) when an
 *       achievement happens. Reason codes are available as constants on
 *       {@link com.tweakdapp.backend.reputation.ReputationReasons}.</li>
 *   <li><strong>Reads:</strong> {@code getSummary}, {@code getHistory} — for the profile screen,
 *       by user id or by username.</li>
 *   <li><strong>Reference data:</strong> {@code listReasons} — the active catalogue, so the client
 *       can render an "how do I earn reputation?" sheet.</li>
 * </ul>
 *
 * <h2>Cross-module dependencies</h2>
 * {@code profile} (score column, username resolution) and {@code shared} (exception hierarchy).
 * Nothing depends on this module yet — the achievement hooks in {@code mapevents} / {@code forums} /
 * {@code garage} are wired up as each achievement is defined.
 */
@ApplicationModule(
        displayName = "Reputation"
)
package com.tweakdapp.backend.reputation;

import org.springframework.modulith.ApplicationModule;

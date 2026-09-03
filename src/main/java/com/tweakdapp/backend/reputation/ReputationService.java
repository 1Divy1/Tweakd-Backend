package com.tweakdapp.backend.reputation;

import com.tweakdapp.backend.reputation.dto.ReputationEntryDto;
import com.tweakdapp.backend.reputation.dto.ReputationHistoryPageDto;
import com.tweakdapp.backend.reputation.dto.ReputationReasonDto;
import com.tweakdapp.backend.reputation.dto.ReputationSource;
import com.tweakdapp.backend.reputation.dto.ReputationSummaryDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reputation reads, and the SPI sibling modules call to award it.
 *
 * <p>Awarding is the interesting half: {@code mapevents} calls it when someone checks in at an
 * event, {@code garage} when the first mod lands on a car, and later contests and the marketplace
 * as those features arrive. Everything else here is read-only, feeding the reputation block and
 * history timeline on a profile.
 *
 * <p>Award through a {@link com.tweakdapp.backend.reputation.dto.ReputationSource} wherever there
 * is a row to point at. It is what lets a timeline entry read "+10 · Attended a car event · Cluj
 * Auto Show" and link through to the event, and it is what makes the award idempotent, so callers
 * can fire it from a listener without guarding against a retry paying twice.
 */
public interface ReputationService {

    // ---- awarding (the SPI) -------------------------------------------------

    /**
     * Awards a user the catalogue's default points for {@code reasonId}, moving their score and
     * writing the matching history entry in one transaction.
     *
     * <p>Call this from inside the achievement's own transaction so the award and the thing that
     * earned it commit together — an accepted forum answer that rolls back must not leave points
     * behind. Negative-point reasons (the {@code moderation} category) are awarded the same way;
     * the score is clamped at zero and the entry records the clamped delta.
     *
     * <p>This sourceless form records "they attended an event" without saying which. Prefer
     * {@link #award(UUID, String, ReputationSource)} wherever there is a row to point at: it makes
     * the entry deep-linkable and, more importantly, makes the award idempotent. Reserve this form
     * for achievements that genuinely have no source — the yearly anniversary, which should be
     * guarded with {@link #hasEarnedSince} instead.
     *
     * <p>A reason marked {@code is_repeatable = false} can only ever be earned once: a second call
     * returns the entry already on file, leaving the score untouched.
     *
     * @param userId   the profile earning it
     * @param reasonId a code from {@code reputation_score_reason_options}, e.g. {@link ReputationReasons#EVENT_ATTENDED}
     * @return the entry just written, or the existing one if this award was already on file
     * @throws com.tweakdapp.backend.reputation.exception.ReputationReasonNotFoundException if the
     *         code is unknown or retired
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if there is no such profile
     */
    ReputationEntryDto award(UUID userId, String reasonId);

    /**
     * As {@link #award(UUID, String)}, but recording <em>which</em> event, thread or car earned it.
     *
     * <p><strong>Idempotent.</strong> The same reason for the same source pays exactly once — a
     * retried listener, a double-tapped check-in or a replayed job all get the entry already on
     * file back, with the score left where it was. That guarantee is a unique index in the
     * database, not a check in this method, so it holds under concurrency too. Callers therefore
     * need no guard of their own:
     *
     * {@snippet :
     * reputationService.award(
     *         userId,
     *         ReputationReasons.EVENT_ATTENDED,
     *         new ReputationSource(ReputationSourceType.CAR_EVENT, eventId, event.getTitle()));
     * }
     *
     * <p>Note what the source is scoped to, because that is what "once" means. Scoping
     * {@link ReputationReasons#FIRST_MOD} to the <em>car</em> rather than the modification is what
     * makes it the first mod: the second modification on that car finds the award already there.
     *
     * @param source the thing that earned it; its label is snapshotted onto the entry
     */
    ReputationEntryDto award(UUID userId, String reasonId, ReputationSource source);

    /**
     * As {@link #award(UUID, String)}, but with an explicit point value instead of the catalogue's
     * default — for achievements whose worth varies with the occasion (the size of an event, the
     * severity of a moderation penalty).
     *
     * <p>The sign is not forced to match the reason's default: a {@code moderation} reason can be
     * softened and an {@code events} one can be scaled up. Zero is refused, because an entry that
     * moves nothing is noise in a timeline whose whole value is that every line means something.
     *
     * @param points the delta to apply; must not be zero
     * @throws com.tweakdapp.backend.reputation.exception.InvalidReputationAwardException if {@code points} is zero
     */
    ReputationEntryDto award(UUID userId, String reasonId, int points);

    /**
     * An explicit point value <em>and</em> a source — {@link #award(UUID, String, int)} with the
     * idempotency of {@link #award(UUID, String, ReputationSource)}.
     *
     * <p>When the award is already on file the stored entry is returned as it stands, so a
     * different {@code points} on a retry does not revise history.
     */
    ReputationEntryDto award(UUID userId, String reasonId, int points, ReputationSource source);

    // ---- revoking -----------------------------------------------------------

    /**
     * Takes back an award, subtracting exactly the points it applied.
     *
     * <p>For facts that get retracted after the points were paid: an event cancelled after it
     * finished, a corrected contest placement, an appealed moderation penalty. The subtraction uses
     * the delta the entry actually applied, so it is precisely symmetric to the award — including
     * for a penalty that had been clamped at the zero floor.
     *
     * <p><strong>This is the second-choice tool.</strong> Most of what looks like a reversal is
     * really an award that fired too early: reputation for organizing an event belongs on
     * {@code markFinished}, not on {@code createEvent}, and attendance belongs on check-in, not on
     * RSVP. An award that only ever fires on something that has already happened never needs
     * revoking. Reach for this when the underlying fact genuinely changes after the event.
     *
     * <p>The entry is kept, not deleted — stamped revoked, subtracted from the score, and hidden
     * from the public timeline. The owner still sees it, with {@code reason}, because a score that
     * silently drops with no explanation is worse than one that explains itself. Revoking also
     * frees the source to be earned again, so an un-cancelled event can re-award cleanly.
     *
     * <p>Idempotent: revoking an award that was never made, or was already revoked, is a no-op
     * returning {@code false}.
     *
     * {@snippet :
     * // event cancelled after it had already finished and paid out
     * reputationService.revoke(organizerId, ReputationReasons.CAR_EVENT_ORGANIZED,
     *         ReputationSource.of(ReputationSourceType.CAR_EVENT, eventId),
     *         "Event was cancelled");
     * }
     *
     * @param reason short explanation, shown to the owner on their own timeline
     * @return {@code true} if an award was found and taken back
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if there is no such profile
     */
    boolean revoke(UUID userId, String reasonId, ReputationSource source, String reason);

    // ---- reads --------------------------------------------------------------

    /**
     * Whether the user has earned {@code reasonId} at any point since {@code since}.
     *
     * <p>The guard for time-boxed reasons that have no source row to dedupe against — chiefly
     * {@link ReputationReasons#ANNUAL_MEMBER_ANNIVERSARY}, which is repeatable by design but must
     * not pay twice in one year:
     *
     * {@snippet :
     * if (!reputationService.hasEarnedSince(userId, ReputationReasons.ANNUAL_MEMBER_ANNIVERSARY,
     *         Instant.now().minus(365, ChronoUnit.DAYS))) {
     *     reputationService.award(userId, ReputationReasons.ANNUAL_MEMBER_ANNIVERSARY);
     * }
     * }
     *
     * <p>Unlike the source-based guarantee this is a read followed by a write, so it is not
     * race-proof on its own. That is fine for the scheduled job it exists for; anything awarded
     * from a request path should carry a source instead.
     */
    boolean hasEarnedSince(UUID userId, String reasonId, Instant since);

    /**
     * The reputation block for a profile screen: total, count, per-category split, last activity.
     *
     * <p>Identical for the owner and for strangers — every figure here counts live entries only, so
     * the breakdown always adds up to the number on the profile and a revocation is invisible in
     * aggregate. Only the timeline distinguishes the two audiences.
     */
    ReputationSummaryDto getSummary(UUID userId);

    /**
     * As {@link #getSummary(UUID)}, resolved by username — what the public profile screen calls.
     *
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if the username does not resolve
     */
    ReputationSummaryDto getSummaryByUsername(String username);

    /**
     * One keyset page of a user's history, newest first — <strong>the privileged view</strong>,
     * including revoked entries.
     *
     * <p>Taking a UUID rather than a username is what makes this the privileged one: it is reached
     * from the caller's own JWT subject, or by staff who already hold the id. Revoked entries carry
     * {@code revokedAt} and {@code revokedReason} so the owner can see why their score moved.
     * Anything rendering someone else's profile must call
     * {@link #getHistoryByUsername(String, String, int)} instead.
     *
     * @param cursor the previous page's {@code nextCursor}, or {@code null} for the first page
     * @param size   requested page size, clamped to a sane maximum
     * @throws com.tweakdapp.backend.reputation.exception.InvalidReputationCursorException if the
     *         token cannot be decoded
     */
    ReputationHistoryPageDto getHistory(UUID userId, String cursor, int size);

    /**
     * One keyset page of a user's history, resolved by username — <strong>the public view</strong>.
     *
     * <p>Revoked entries are omitted entirely, not flagged. Surfacing "this award was reverted" to
     * strangers would turn the timeline into a shaming mechanic; what other people get to see is
     * simply what still stands.
     *
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if the username does not resolve
     */
    ReputationHistoryPageDto getHistoryByUsername(String username, String cursor, int size);

    // ---- reference data -----------------------------------------------------

    /**
     * The active catalogue, ordered by category then value — the "how reputation works" sheet.
     * Retired reasons are excluded; they remain readable on the history entries that earned them.
     */
    List<ReputationReasonDto> listReasons();
}

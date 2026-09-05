package com.tweakdapp.backend.badges;

import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.BadgeGrantDto;
import com.tweakdapp.backend.badges.dto.BadgeUpsertRequest;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;

import java.util.List;
import java.util.UUID;

/**
 * Badge reads, the SPI sibling modules call to unlock one, and the administration the dashboard
 * drives.
 *
 * <p>Nothing here is reachable by a user acting on themselves. The module's own controller is
 * read-only; a badge is either awarded in-process by the module that witnessed the achievement, or
 * granted by hand from the dashboard by staff who hold the capability for it. Authorisation for
 * the latter is the {@code admin} module's job — the methods below trust their caller, exactly as
 * {@code ProfileService.banUser} does.
 *
 * <p>Every {@link BadgeDto} that comes out of here carries <strong>full public artwork URLs</strong>,
 * built from the R2 keys the database stores. Callers never see a key.
 */
public interface BadgeService {

    // ---- awarding (the SPI) -------------------------------------------------

    /**
     * Unlocks a badge for a user.
     *
     * <p>Call it from inside the achievement's own transaction, so the badge and the thing that
     * earned it commit together — a car import that rolls back must not leave a badge behind.
     *
     * <p><strong>Idempotent.</strong> A user who already holds the badge gets the row they already
     * have back, with {@code earnedAt} unchanged: a retried listener, a replayed job or a
     * double-tapped action all collapse onto the first unlock. That guarantee is the unique index
     * on {@code (user_id, badge_id)}, not a check in this method, so it holds under concurrency
     * too — callers need no guard of their own.
     *
     * <p>Because a badge is a current-state fact rather than a ledger entry, "already earned" is
     * success, not a conflict. There is no second award to record and nothing for a caller to
     * handle differently.
     *
     * @param userId  the profile unlocking it
     * @param badgeId a code from the {@code badges} table, e.g. {@link Badges#PIONEER}
     * <p>The user is taken on trust: this module does not read {@code profiles}, so that
     * {@code profile} can depend on it (the profile response carries a user's badges). An id that
     * is not a profile fails on the foreign key rather than with a tidy 404 — callers award from
     * inside the transaction that just handled that user, so there is nothing to look up.
     *
     * @return the badge as the user now holds it
     * @throws com.tweakdapp.backend.badges.exception.BadgeNotFoundException if the code is unknown
     *         or the badge has been retired
     */
    UserBadgeDto award(UUID userId, String badgeId);

    /**
     * As {@link #award(UUID, String)}, but recording the staff member who granted it by hand.
     *
     * <p>For badges that are a judgement call rather than something a rule can detect. The granter
     * is stamped on {@code user_badges.granted_by} so a privileged write to someone else's profile
     * can be reviewed afterwards; {@code null} there means the backend awarded it automatically.
     *
     * @param grantedBy the granting staff member's id, from {@code admin_team_members}
     */
    UserBadgeDto grant(UUID userId, String badgeId, UUID grantedBy);

    /**
     * Takes a badge away, if the user holds it.
     *
     * <p>For a badge granted by mistake, or to the wrong account. Unlike a reputation revocation
     * this deletes the row outright — a badge is a statement about the present, not a ledger entry,
     * so there is no arithmetic to keep symmetric and no tombstone worth keeping. The badge becomes
     * earnable again immediately.
     *
     * <p>Idempotent: revoking a badge the user does not hold is a no-op returning {@code false}.
     *
     * @return {@code true} if a badge was actually taken away
     */
    boolean revoke(UUID userId, String badgeId);

    // ---- reads --------------------------------------------------------------

    /**
     * Whether the user currently holds this badge — the cheap check for a caller that wants to skip
     * work before awarding (sending a congratulation, say). Not needed for {@link #award}'s own
     * safety; that is already idempotent.
     */
    boolean hasBadge(UUID userId, String badgeId);

    /**
     * Every badge a user holds, newest unlock first.
     *
     * <p>Includes badges that have since been <em>retired</em> from the catalogue: earning one is a
     * fact about the user, and taking it off their profile because the badge is no longer offered
     * would be rewriting history. Retiring only stops new awards.
     */
    List<UserBadgeDto> listUserBadges(UUID userId);

    /**
     * The badges the user has earned but whose one-time unlock animation the app still owes them —
     * oldest unlock first.
     *
     * <p>The app calls this on launch, plays the Duolingo-style celebration for each, and then
     * acknowledges each one through {@link #markCelebrated(UUID, String)} so it never plays again.
     * A badge is on this list from the moment it is awarded — automatically or by hand from the
     * dashboard — until that acknowledgement.
     *
     * <p>The caller's own only, like {@link #listLockedBadges(UUID)}: a pending celebration is a
     * client-state detail, not something anyone else can see.
     */
    List<UserBadgeDto> listPendingCelebrations(UUID userId);

    /**
     * Records that the app has finished the unlock animation for one of the user's badges, so it
     * drops off {@link #listPendingCelebrations(UUID)} and does not animate again.
     *
     * <p><strong>Idempotent.</strong> Calling it twice, or for a badge the user does not hold, is a
     * no-op that returns {@code false}. It cannot award, change or reveal a badge — the only thing
     * it can do is flip an already-held badge's celebration latch on.
     *
     * @return {@code true} if this call is what flipped the latch; {@code false} if it was already
     *         set or there was no such held badge
     */
    boolean markCelebrated(UUID userId, String badgeId);

    /**
     * The badges a user has <em>not</em> unlocked yet — every available badge they don't hold,
     * oldest first.
     *
     * <p>Backs the locked section of the user's <strong>own</strong> profile: locked artwork plus
     * the description, which is where "here is what to do to unlock this" lives. Deliberately not
     * offered for anyone else — what someone has yet to achieve is not a stranger's business, and
     * the whole catalogue is already public through {@link #listCatalogue()}.
     *
     * <p>Retired badges are absent: something that can no longer be earned is not a goal. A badge
     * retired after the user earned it still shows in {@link #listUserBadges(UUID)}.
     */
    List<BadgeDto> listLockedBadges(UUID userId);

    /**
     * The catalogue of badges that can currently be unlocked, oldest first.
     *
     * <p>Retired badges are excluded. Each entry carries both artwork URLs, so a client rendering a
     * "badges you could earn" sheet has everything it needs to show locked and unlocked states.
     */
    List<BadgeDto> listCatalogue();

    // ---- administration (called by the admin module; no auth logic here) ----

    /**
     * The whole catalogue including retired badges, oldest first — the dashboard's list, which has
     * to show what was retired in order to un-retire it.
     */
    List<BadgeDto> listAllForAdmin();

    /**
     * Adds a badge to the catalogue.
     *
     * @param badgeId the stable code; it is referenced by every unlock, so it is chosen once and
     *                not editable afterwards
     * @throws com.tweakdapp.backend.badges.exception.BadgeAlreadyExistsException if the code is taken
     */
    BadgeDto create(String badgeId, BadgeUpsertRequest request);

    /**
     * Edits a badge definition in place. The code is not editable — every unlock references it.
     *
     * <p>Editing is a full replace of the mutable fields, so the dashboard sends the whole shape
     * back. Flipping {@code available} to {@code false} is how a badge is retired: it stops being
     * awardable and drops out of the catalogue, while the profiles holding it are untouched.
     *
     * @throws com.tweakdapp.backend.badges.exception.BadgeNotFoundException if the code is unknown
     */
    BadgeDto update(String badgeId, BadgeUpsertRequest request);

    /**
     * Removes a badge from the catalogue entirely.
     *
     * <p>Only possible while nobody holds it — a badge someone earned must stay readable on their
     * profile, so the moment it has been awarded, retiring ({@code available = false}) is the only
     * way to withdraw it.
     *
     * @throws com.tweakdapp.backend.badges.exception.BadgeNotFoundException if the code is unknown
     * @throws com.tweakdapp.backend.badges.exception.BadgeInUseException if any user holds it
     */
    void delete(String badgeId);

    /**
     * What one user holds, with the granting staff member on each row — the dashboard's view of a
     * user's badges.
     *
     * <p>The granter is <strong>staff-only</strong>, which is why it needs its own read: nothing
     * the app returns carries it. A badge on a profile is the user's achievement, not a note about
     * which staff member typed it in, and "granted by X" on a public profile would read as a
     * favour rather than a badge. It is recorded so a hand-grant can be traced afterwards, and
     * that is all.
     */
    List<BadgeGrantDto> listGrantsForAdmin(UUID userId);

    /** How many users hold each badge, keyed by badge code — the dashboard's holder counts. */
    java.util.Map<String, Long> holderCounts();
}

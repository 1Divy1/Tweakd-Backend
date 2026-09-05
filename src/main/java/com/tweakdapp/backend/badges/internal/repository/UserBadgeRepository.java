package com.tweakdapp.backend.badges.internal.repository;

import com.tweakdapp.backend.badges.internal.entity.UserBadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and deletes over the badges users hold.
 *
 * <p>Not paginated, deliberately. A user holds a handful of badges — the whole set is one small
 * query served by the {@code (user_id, badge_id)} unique index, and a cursor would be machinery
 * around a list that fits on one screen. Reputation history pages because it grows without bound;
 * this does not.
 */
public interface UserBadgeRepository extends JpaRepository<UserBadgeEntity, UUID> {

    /**
     * Everything a user holds, newest unlock first. {@code join fetch} on the badge because every
     * row renders its title and artwork — without it this is N+1 across the list.
     *
     * <p>Retired badges are included on purpose: earning one is a fact about the user, and
     * withdrawing a badge from the catalogue must not rewrite the profiles that already earned it.
     */
    @Query("""
            select ub from UserBadgeEntity ub
            join fetch ub.badge
            where ub.userId = :userId
            order by ub.createdAt desc, ub.id desc
            """)
    List<UserBadgeEntity> findAllForUser(@Param("userId") UUID userId);

    /**
     * The user's unlock of one specific badge, if they hold it — the idempotency lookup behind
     * {@code award}. It mirrors the unique index on {@code (user_id, badge_id)}, which enforces the
     * same thing under a race that this read would lose.
     */
    @Query("""
            select ub from UserBadgeEntity ub
            join fetch ub.badge
            where ub.userId = :userId and ub.badge.id = :badgeId
            """)
    Optional<UserBadgeEntity> findForUserAndBadge(@Param("userId") UUID userId,
                                                  @Param("badgeId") String badgeId);

    boolean existsByUserIdAndBadgeId(UUID userId, String badgeId);

    /** How many users hold one badge — the guard behind refusing to delete a badge in use. */
    long countByBadgeId(String badgeId);

    /**
     * The user's unlocks whose in-app celebration is still owed — earned, but the client has not yet
     * played the animation. Oldest unlock first, so a client that has fallen several badges behind
     * animates them in the order they were earned.
     *
     * <p>{@code join fetch} on the badge for the same reason as the other reads: every row renders
     * its title and artwork. Filtered on {@code user_id} through the {@code (user_id, badge_id)}
     * unique index, then {@code granted_in_app} in memory over the handful that returns — no index
     * of its own, matching how the module treats every other per-user badge read.
     */
    @Query("""
            select ub from UserBadgeEntity ub
            join fetch ub.badge
            where ub.userId = :userId and ub.grantedInApp = false
            order by ub.createdAt asc, ub.id asc
            """)
    List<UserBadgeEntity> findPendingCelebrationForUser(@Param("userId") UUID userId);

    /**
     * Marks one of the user's badges as celebrated — the client's acknowledgement that it has
     * finished the unlock animation. A bulk update rather than a load-mutate-flush because there is
     * nothing to read back and the write is a single boolean flip.
     *
     * <p>Idempotent by construction: the {@code granted_in_app = false} predicate means a second
     * call, or a call for a badge the user does not hold, matches no row and returns {@code 0}.
     *
     * @return the number of rows flipped — 1 on the first acknowledgement, 0 on every repeat
     */
    @Modifying
    @Query("""
            update UserBadgeEntity ub
            set ub.grantedInApp = true
            where ub.userId = :userId and ub.badge.id = :badgeId and ub.grantedInApp = false
            """)
    int markCelebrated(@Param("userId") UUID userId, @Param("badgeId") String badgeId);
}

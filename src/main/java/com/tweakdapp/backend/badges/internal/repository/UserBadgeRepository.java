package com.tweakdapp.backend.badges.internal.repository;

import com.tweakdapp.backend.badges.internal.entity.UserBadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

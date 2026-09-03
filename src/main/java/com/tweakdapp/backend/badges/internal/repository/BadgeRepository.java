package com.tweakdapp.backend.badges.internal.repository;

import com.tweakdapp.backend.badges.internal.entity.BadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads over the badge catalogue.
 *
 * <p>Two flavours of the list, because the app and the dashboard want different things: the client
 * only ever sees badges it can still unlock, while the dashboard has to see retired ones in order
 * to bring one back.
 */
public interface BadgeRepository extends JpaRepository<BadgeEntity, String> {

    /** The catalogue as the app sees it — awardable badges only, oldest first. */
    List<BadgeEntity> findByAvailableTrueOrderByCreatedAtAsc();

    /** Every badge, retired ones included — the dashboard's list. */
    List<BadgeEntity> findAllByOrderByCreatedAtAsc();

    /** A badge that may still be awarded; a retired one resolves as empty here. */
    Optional<BadgeEntity> findByIdAndAvailableTrue(String id);

    /**
     * The awardable badges one user has not unlocked — the locked section of their own profile.
     *
     * <p>An anti-join rather than "fetch the catalogue, fetch their badges, subtract in Java":
     * the difference is one query instead of two, and the {@code not exists} is served by the
     * {@code (user_id, badge_id)} unique index.
     *
     * <p>Retired badges are excluded. Something that can no longer be earned is not a goal, even
     * though one earned before it was retired stays on the profile that holds it.
     */
    @Query("""
            select b from BadgeEntity b
            where b.available = true
              and not exists (
                  select 1 from UserBadgeEntity ub
                  where ub.badge = b and ub.userId = :userId
              )
            order by b.createdAt asc
            """)
    List<BadgeEntity> findLockedForUser(@Param("userId") UUID userId);

    /**
     * How many users hold each badge, as {@code [badgeId, count]} rows — the dashboard's holder
     * counts in one round trip. Badges nobody holds are absent rather than zero; the caller fills
     * those in, which is cheaper than an outer join over the whole catalogue.
     */
    @Query("select ub.badge.id, count(ub) from UserBadgeEntity ub group by ub.badge.id")
    List<Object[]> countHoldersByBadge();
}

package com.tweakdapp.backend.badges.internal.repository;

import com.tweakdapp.backend.badges.internal.entity.BadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * The catalogue as the app sees it — badges that can be unlocked right now, oldest first.
     *
     * <p>"Right now" is both halves of awardability: available, and inside its offer window. A
     * limited-time badge therefore leaves the catalogue on its own the moment it expires, the same
     * way a retired one does, so the app never advertises something it can no longer hand out.
     */
    @Query("""
            select b from BadgeEntity b
            where b.available = true
              and (b.earnableFrom  is null or b.earnableFrom  <= :at)
              and (b.earnableUntil is null or b.earnableUntil >  :at)
            order by b.createdAt asc
            """)
    List<BadgeEntity> findEarnableAt(@Param("at") Instant at);

    /** Every badge, retired and expired ones included — the dashboard's list. */
    List<BadgeEntity> findAllByOrderByCreatedAtAsc();

    /**
     * A badge staff may still grant by hand: available, whatever its window says.
     *
     * <p>The window bounds the <em>automatic</em> offer, not what staff can do. A hand-grant after
     * a badge has expired is precisely the case hand-granting exists for — an account recreated
     * after a support issue, a user who signed up in the window and hit a bug — and it is already
     * accountable, because {@code granted_by} records who did it. Retiring is the switch that stops
     * a badge being handed out at all, and that one this respects.
     */
    Optional<BadgeEntity> findByIdAndAvailableTrue(String id);

    /**
     * The badges one user has not unlocked that can be unlocked at {@code at} — the locked section
     * of their own profile.
     *
     * <p>An anti-join rather than "fetch the catalogue, fetch their badges, subtract in Java":
     * the difference is one query instead of two, and the {@code not exists} is served by the
     * {@code (user_id, badge_id)} unique index.
     *
     * <p>Retired and expired badges are both excluded. Something that can no longer be earned is
     * not a goal, and showing it under "here is how to unlock this" would be an instruction that
     * does not work any more. One earned before it was withdrawn still shows on the profile that
     * holds it.
     */
    @Query("""
            select b from BadgeEntity b
            where b.available = true
              and (b.earnableFrom  is null or b.earnableFrom  <= :at)
              and (b.earnableUntil is null or b.earnableUntil >  :at)
              and not exists (
                  select 1 from UserBadgeEntity ub
                  where ub.badge = b and ub.userId = :userId
              )
            order by b.createdAt asc
            """)
    List<BadgeEntity> findLockedForUser(@Param("userId") UUID userId, @Param("at") Instant at);

    /**
     * Every badge a trigger should unlock for this user: bound to the trigger, awardable at
     * {@code at}, and not already held. The whole decision behind {@code awardForTrigger}, in one
     * query.
     *
     * <p>Doing it in SQL rather than filtering the catalogue in Java is what makes the check and
     * the insert see the same rows, and it means the common case — a user who already holds
     * everything their signup unlocks — costs one indexed read and no writes at all.
     *
     * <p>The {@code not exists} is the same anti-join as {@link #findLockedForUser}, served by the
     * {@code (user_id, badge_id)} unique index. It is an optimisation, not the safety net: that
     * index is what actually stops a double award, which is why a caller needs no guard even
     * though this read and the insert that follows are two statements.
     *
     * @param trigger the {@code BadgeTrigger} code, matched against {@code badges.award_trigger}
     * @param at      the moment the achievement happened, judged against each badge's window
     */
    @Query("""
            select b from BadgeEntity b
            where b.available = true
              and b.awardTrigger = :trigger
              and (b.earnableFrom  is null or b.earnableFrom  <= :at)
              and (b.earnableUntil is null or b.earnableUntil >  :at)
              and not exists (
                  select 1 from UserBadgeEntity ub
                  where ub.badge = b and ub.userId = :userId
              )
            order by b.createdAt asc
            """)
    List<BadgeEntity> findUnheldForTrigger(@Param("trigger") String trigger,
                                           @Param("at") Instant at,
                                           @Param("userId") UUID userId);

    /**
     * How many users hold each badge, as {@code [badgeId, count]} rows — the dashboard's holder
     * counts in one round trip. Badges nobody holds are absent rather than zero; the caller fills
     * those in, which is cheaper than an outer join over the whole catalogue.
     */
    @Query("select ub.badge.id, count(ub) from UserBadgeEntity ub group by ub.badge.id")
    List<Object[]> countHoldersByBadge();
}

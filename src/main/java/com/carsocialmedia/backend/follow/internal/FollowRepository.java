package com.carsocialmedia.backend.follow.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface FollowRepository extends JpaRepository<FollowEntity, FollowId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FollowEntity f where f.id.followerId = :followerId and f.id.followingId = :followingId")
    Optional<FollowEntity> findForUpdate(@Param("followerId") UUID followerId,
                                        @Param("followingId") UUID followingId);

    @Query("select f.id.followerId from FollowEntity f " +
            "where f.id.followingId = :followingId and f.status = 'pending' " +
            "order by f.createdAt desc")
    List<UUID> findPendingFollowerIds(@Param("followingId") UUID followingId);

    @Query("select f.id.followerId from FollowEntity f " +
            "where f.id.followingId = :followingId and f.status = 'accepted' " +
            "order by f.createdAt desc")
    List<UUID> findAcceptedFollowerIds(@Param("followingId") UUID followingId);

    @Query("select f.id.followingId from FollowEntity f " +
            "where f.id.followerId = :followerId and f.status = 'accepted' " +
            "order by f.createdAt desc")
    List<UUID> findAcceptedFollowingIds(@Param("followerId") UUID followerId);

    /**
     * Of the given {@code candidateIds}, returns the subset that {@code followerId} already
     * follows with an accepted status. Used to populate the per-row {@code isFollowing} flag
     * on followers/following lists in a single query (avoids an N+1 per profile).
     */
    @Query("select f.id.followingId from FollowEntity f " +
            "where f.id.followerId = :followerId and f.status = 'accepted' " +
            "and f.id.followingId in :candidateIds")
    List<UUID> findAcceptedFollowingIdsIn(@Param("followerId") UUID followerId,
                                          @Param("candidateIds") List<UUID> candidateIds);

    boolean existsByIdFollowerIdAndIdFollowingIdAndStatus(UUID followerId,
                                                         UUID followingId,
                                                         String status);

    /**
     * Bulk transition pending → accepted for all requests targeted at {@code followingId}.
     * The {@code handle_follow_change} trigger increments the counters per-row, so we
     * intentionally do NOT touch counters from the application side.
     */
    @Modifying
    @Query("update FollowEntity f set f.status = 'accepted' " +
            "where f.id.followingId = :followingId and f.status = 'pending'")
    int acceptAllPendingFor(@Param("followingId") UUID followingId);
}

package com.carsocialmedia.backend.relationships.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface RelationshipRepository extends JpaRepository<RelationshipEntity, RelationshipId> {

    @Query("select f.id.followerId from RelationshipEntity f " +
            "where f.id.followingId = :followingId and f.status = 'accepted' " +
            "order by f.createdAt desc")
    List<UUID> findAcceptedFollowerIds(@Param("followingId") UUID followingId);

    @Query("select f.id.followingId from RelationshipEntity f " +
            "where f.id.followerId = :followerId and f.status = 'accepted' " +
            "order by f.createdAt desc")
    List<UUID> findAcceptedFollowingIds(@Param("followerId") UUID followerId);

    /**
     * Of the given {@code candidateIds}, returns the subset that {@code followerId} already
     * follows with an accepted status. Used to populate the per-row {@code isFollowing} flag
     * on followers/following lists in a single query (avoids an N+1 per profile).
     */
    @Query("select f.id.followingId from RelationshipEntity f " +
            "where f.id.followerId = :followerId and f.status = 'accepted' " +
            "and f.id.followingId in :candidateIds")
    List<UUID> findAcceptedFollowingIdsIn(@Param("followerId") UUID followerId,
                                          @Param("candidateIds") List<UUID> candidateIds);
}

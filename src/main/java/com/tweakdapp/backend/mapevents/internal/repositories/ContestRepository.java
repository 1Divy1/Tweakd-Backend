package com.tweakdapp.backend.mapevents.internal.repositories;

import java.util.Collection;

import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContestRepository extends JpaRepository<ContestEntity, UUID> {

    /** Every contest of an event, in the order the tab shows them within a status group. */
    @Query("""
            select c
              from ContestEntity c
              join fetch c.category
             where c.eventId = :eventId
             order by c.opensAt asc, c.createdAt asc
            """)
    List<ContestEntity> findByEventId(@Param("eventId") UUID eventId);

    @Query("""
            select c
              from ContestEntity c
              join fetch c.category
             where c.id = :contestId
               and c.eventId = :eventId
            """)
    Optional<ContestEntity> findByIdAndEventId(@Param("contestId") UUID contestId, @Param("eventId") UUID eventId);

    /**
     * Loads a contest {@code FOR UPDATE}. Every status transition goes through this, so two
     * organizers tapping "finish now" at the same moment serialise rather than both paying out.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContestEntity c where c.id = :contestId")
    Optional<ContestEntity> lockById(@Param("contestId") UUID contestId);

    /** Ids of an event's contests in a given status — what finishing or cancelling the event has to close. */
    @Query("select c.id from ContestEntity c where c.eventId = :eventId and c.status = :status")
    List<UUID> findIdsByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") String status);

    long countByEventIdAndStatusNot(UUID eventId, String status);

    @Query("select c from ContestEntity c join fetch c.category where c.id in :ids")
    List<ContestEntity> findAllWithCategoryByIdIn(@Param("ids") List<UUID> ids);

    /** Contests of several events in one status, category fetched — how cards read every finished contest at once. */
    @Query("""
            select c
              from ContestEntity c
              join fetch c.category
             where c.eventId in :eventIds
               and c.status = :status
            """)
    List<ContestEntity> findByEventIdInAndStatus(@Param("eventIds") Collection<UUID> eventIds,
                                                 @Param("status") String status);
}

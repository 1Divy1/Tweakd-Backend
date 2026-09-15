package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantId;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface MapEventParticipantRepository extends JpaRepository<MapEventParticipantEntity, MapEventParticipantId> {

    /**
     * One keyset page of the cars entered into an event, newest registration first, optionally
     * narrowed to one status — the public line-up asks for {@code accepted}, an organizer's review
     * screen asks for {@code pending}.
     */
    @Query("""
            select p
              from MapEventParticipantEntity p
             where p.id.eventId = :eventId
               and (cast(:status as string) is null or p.status = :status)
               and p.ownerId not in :hiddenIds
               and (cast(:cursorCreatedAt as timestamp) is null
                    or p.createdAt < :cursorCreatedAt
                    or (p.createdAt = :cursorCreatedAt and p.id.carId < :cursorCarId))
             order by p.createdAt desc, p.id.carId desc
            """)
    List<MapEventParticipantEntity> findPage(@Param("eventId") UUID eventId,
                                             @Param("status") String status,
                                             @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                             @Param("cursorCarId") UUID cursorCarId,
                                             @Param("hiddenIds") Collection<UUID> hiddenIds,
                                             Limit limit);

    /**
     * One keyset page of the public line-up: {@code accepted} cars plus {@code withdrawn} ones,
     * since a pending withdrawal does not remove a participant from the list, only flags it.
     */
    @Query("""
            select p
              from MapEventParticipantEntity p
             where p.id.eventId = :eventId
               and p.status in ('accepted', 'withdrawn')
               and p.ownerId not in :hiddenIds
               and (cast(:cursorCreatedAt as timestamp) is null
                    or p.createdAt < :cursorCreatedAt
                    or (p.createdAt = :cursorCreatedAt and p.id.carId < :cursorCarId))
             order by p.createdAt desc, p.id.carId desc
            """)
    List<MapEventParticipantEntity> findLineupPage(@Param("eventId") UUID eventId,
                                                   @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                                   @Param("cursorCarId") UUID cursorCarId,
                                                   @Param("hiddenIds") Collection<UUID> hiddenIds,
                                                   Limit limit);

    /** The caller's own registrations for one event — shown back to them whatever their status. */
    List<MapEventParticipantEntity> findByIdEventIdAndOwnerId(UUID eventId, UUID ownerId);

    /** One owner's rows for an event in a given status — e.g. their accepted cars, or their withdrawal request. */
    List<MapEventParticipantEntity> findByIdEventIdAndOwnerIdAndStatus(UUID eventId, UUID ownerId, String status);

    /** Every row across all owners in a given status — the organizer's withdrawal review queue. */
    List<MapEventParticipantEntity> findByIdEventIdAndStatus(UUID eventId, String status);

    long countByIdEventIdAndStatus(UUID eventId, String status);

    /** One car's rows across every event in a given status — the car's attendance history. */
    List<MapEventParticipantEntity> findByIdCarIdAndStatus(UUID carId, String status);
}

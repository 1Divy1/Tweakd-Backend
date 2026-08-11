package com.carsocialmedia.backend.mapevents.internal.repositories;

import com.carsocialmedia.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.carsocialmedia.backend.mapevents.internal.entities.MapEventParticipantId;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
               and (cast(:cursorCreatedAt as timestamp) is null
                    or p.createdAt < :cursorCreatedAt
                    or (p.createdAt = :cursorCreatedAt and p.id.carId < :cursorCarId))
             order by p.createdAt desc, p.id.carId desc
            """)
    List<MapEventParticipantEntity> findPage(@Param("eventId") UUID eventId,
                                             @Param("status") String status,
                                             @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                             @Param("cursorCarId") UUID cursorCarId,
                                             Limit limit);

    /** The caller's own registrations for one event — shown back to them whatever their status. */
    List<MapEventParticipantEntity> findByIdEventIdAndOwnerId(UUID eventId, UUID ownerId);

    long countByIdEventIdAndStatus(UUID eventId, String status);
}

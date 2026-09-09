package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeId;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MapEventAttendeeRepository extends JpaRepository<MapEventAttendeeEntity, MapEventAttendeeId> {

    /**
     * One keyset page of an event's attendees, most recent RSVP first, optionally narrowed to one
     * status so the client can show "going" and "interested" as separate tabs.
     */
    @Query("""
            select a
              from MapEventAttendeeEntity a
             where a.id.eventId = :eventId
               and (cast(:status as string) is null or a.status = :status)
               and (cast(:cursorCreatedAt as timestamp) is null
                    or a.createdAt < :cursorCreatedAt
                    or (a.createdAt = :cursorCreatedAt and a.id.userId < :cursorUserId))
             order by a.createdAt desc, a.id.userId desc
            """)
    List<MapEventAttendeeEntity> findPage(@Param("eventId") UUID eventId,
                                          @Param("status") String status,
                                          @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                          @Param("cursorUserId") UUID cursorUserId,
                                          Limit limit);

    long countByIdEventIdAndStatus(UUID eventId, String status);

    /** Everyone with a given RSVP on an event — the audience for a contest opening or its result. */
    @Query("select a.id.userId from MapEventAttendeeEntity a where a.id.eventId = :eventId and a.status = :status")
    List<UUID> findUserIdsByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") String status);
}

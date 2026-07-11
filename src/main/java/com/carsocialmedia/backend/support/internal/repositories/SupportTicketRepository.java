package com.carsocialmedia.backend.support.internal.repositories;

import com.carsocialmedia.backend.support.internal.entities.SupportTicketEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, UUID> {

    // Both lists paginate on (last_message_at, id) desc — most recently active first. The service
    // loads size + 1 rows and trims the sentinel.

    @Query("""
            select t from SupportTicketEntity t
            where t.userId = :userId
              and (:afterActivity is null
                   or t.lastMessageAt < :afterActivity
                   or (t.lastMessageAt = :afterActivity and t.id < :afterId))
            order by t.lastMessageAt desc, t.id desc
            """)
    List<SupportTicketEntity> findMine(@Param("userId") UUID userId,
                                       @Param("afterActivity") Instant afterActivity,
                                       @Param("afterId") UUID afterId,
                                       Pageable pageable);

    @Query("""
            select t from SupportTicketEntity t
            where (:status is null or t.status = :status)
              and (:afterActivity is null
                   or t.lastMessageAt < :afterActivity
                   or (t.lastMessageAt = :afterActivity and t.id < :afterId))
            order by t.lastMessageAt desc, t.id desc
            """)
    List<SupportTicketEntity> findQueue(@Param("status") String status,
                                        @Param("afterActivity") Instant afterActivity,
                                        @Param("afterId") UUID afterId,
                                        Pageable pageable);

    long countByStatus(String status);

    long countByResolvedAtAfter(Instant since);
}

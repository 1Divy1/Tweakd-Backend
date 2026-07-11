package com.carsocialmedia.backend.admin.internal.repositories;

import com.carsocialmedia.backend.admin.internal.entities.ModerationCaseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ModerationCaseRepository extends JpaRepository<ModerationCaseEntity, Long> {

    /**
     * One keyset page of the moderation queue, most recently reported first. Null filters and a
     * null cursor are tolerated in-query so first page / filtered / unfiltered all share one query.
     * {@code statuses} is a list so "needs attention" can cover both {@code open} and
     * {@code escalated} in one call.
     */
    @Query("""
            select c from ModerationCaseEntity c
            where c.status in :statuses
              and (:type is null or c.targetType = :type)
              and (cast(:cursorAt as timestamp) is null
                   or c.lastReportedAt < :cursorAt
                   or (c.lastReportedAt = :cursorAt and c.id < :cursorId))
            order by c.lastReportedAt desc, c.id desc
            """)
    List<ModerationCaseEntity> findQueue(@Param("statuses") List<String> statuses,
                                         @Param("type") String type,
                                         @Param("cursorAt") Instant cursorAt,
                                         @Param("cursorId") Long cursorId,
                                         Pageable pageable);

    /** Open + escalated cases per target type, for the queue's filter-tab badges. */
    @Query("""
            select c.targetType as key, count(c) as count from ModerationCaseEntity c
            where c.status in ('open', 'escalated')
            group by c.targetType
            """)
    List<KeyCount> countPendingByType();

    @Query("select c.status as key, count(c) as count from ModerationCaseEntity c group by c.status")
    List<KeyCount> countByStatus();

    long countByStatusIn(List<String> statuses);

    interface KeyCount {
        String getKey();
        long getCount();
    }
}

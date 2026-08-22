package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.MapEventRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MapEventRuleRepository extends JpaRepository<MapEventRuleEntity, UUID> {

    List<MapEventRuleEntity> findByEventIdOrderBySortOrderAsc(UUID eventId);

    /** Wipes an event's rules ahead of a whole-list replace. */
    @Modifying
    @Query("delete from MapEventRuleEntity r where r.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}

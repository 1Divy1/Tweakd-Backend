package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.MapEventOrganizerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MapEventOrganizerRepository extends JpaRepository<MapEventOrganizerEntity, UUID> {

    /** Every organizer of an event — creator first, then the rest oldest-added first. */
    List<MapEventOrganizerEntity> findByEventIdOrderByRoleAscCreatedAtAsc(UUID eventId);

    /**
     * Whether a user may act as an organizer of this event. Only individual organizers are
     * considered: business accounts have no login, so nobody can be acting "as" one.
     */
    boolean existsByEventIdAndIndividualOrganizerId(UUID eventId, UUID individualOrganizerId);

    boolean existsByEventIdAndBusinessOrganizerId(UUID eventId, UUID businessOrganizerId);

    Optional<MapEventOrganizerEntity> findByEventIdAndId(UUID eventId, UUID id);
}

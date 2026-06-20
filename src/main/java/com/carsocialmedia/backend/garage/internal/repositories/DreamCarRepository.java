package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.DreamCarEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link DreamCarEntity}. All queries are scoped to a profile so a user
 * can only ever read or mutate their own dream cars.
 */
public interface DreamCarRepository extends JpaRepository<DreamCarEntity, UUID> {

    /** A user's dream cars, oldest first. */
    List<DreamCarEntity> findByProfileIdOrderByCreatedAtAsc(UUID profileId);

    /** A single dream car, scoped to its owner (returns empty if it belongs to someone else). */
    Optional<DreamCarEntity> findByIdAndProfileId(UUID id, UUID profileId);
}
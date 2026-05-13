package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.GarageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link GarageEntity} persistence operations.
 *
 * Provides CRUD operations and custom queries for garage lookups.
 * One garage per user (via owner_id FK to profiles.id).
 */
public interface GarageRepository extends JpaRepository<GarageEntity, UUID> {
    /**
     * Finds the garage owned by the specified user.
     *
     * @param ownerId the user UUID (references profiles.id)
     * @return the garage, or empty if the user has no garage (rare)
     */
    Optional<GarageEntity> findByOwnerId(UUID ownerId);
}
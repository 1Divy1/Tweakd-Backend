package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.GarageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GarageRepository extends JpaRepository<GarageEntity, UUID> {
    Optional<GarageEntity> findByOwnerId(UUID ownerId);
}
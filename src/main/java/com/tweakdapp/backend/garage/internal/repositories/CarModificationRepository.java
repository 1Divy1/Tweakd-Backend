package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarModificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link CarModificationEntity} persistence operations.
 *
 * Provides CRUD operations and custom queries for modifications.
 * Custom queries use join fetch to eagerly load the category reference,
 * preventing lazy-loading issues during DTO mapping.
 */
public interface CarModificationRepository extends JpaRepository<CarModificationEntity, UUID> {

    /**
     * Finds all modifications for a car, ordered by installation date (newest first),
     * then by creation date.
     *
     * Uses join fetch to eagerly load the category to prevent lazy-loading exceptions
     * during serialization.
     *
     * @param carId the car ID
     * @return list of modifications for the car, ordered by installation date desc
     */
    @Query("""
            select m
              from CarModificationEntity m
              join fetch m.category
             where m.car.id = :carId
             order by m.installationDate desc, m.createdAt desc
            """)
    List<CarModificationEntity> findByCarIdWithCategory(@Param("carId") UUID carId);
}

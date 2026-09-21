package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarModificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /**
     * Batch-loads modifications by id with their category and car, for assembling a page of feed
     * cards without a query per card. Ids that no longer exist are simply absent.
     *
     * @param ids the modification IDs
     * @return the matching modifications, category and car eagerly loaded
     */
    @Query("""
            select m
              from CarModificationEntity m
              join fetch m.category
              join fetch m.car
             where m.id in :ids
            """)
    List<CarModificationEntity> findAllByIdsWithCategoryAndCar(@Param("ids") Collection<UUID> ids);

    /** The car a modification sits on, but only if the given user owns it — the share gate. */
    @Query("""
            select m.car.id
              from CarModificationEntity m
             where m.id = :modificationId
               and m.car.garage.ownerId = :ownerId
            """)
    Optional<UUID> findCarIdByIdAndOwner(@Param("modificationId") UUID modificationId, @Param("ownerId") UUID ownerId);
}

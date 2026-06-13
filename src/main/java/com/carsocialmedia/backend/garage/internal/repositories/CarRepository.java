package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CarEntity} persistence operations.
 *
 * Provides CRUD operations and custom queries optimized for garage and car views.
 * Custom queries use join fetch to eagerly load references within the transaction,
 * avoiding lazy-loading issues when mapping to DTOs.
 */
public interface CarRepository extends JpaRepository<CarEntity, UUID> {

    /**
     * Garage list view. Fetches only the columns and joins the summary projection needs
     * (brand and model names), avoiding lazy hops when the controller serializes.
     */
    @Query("""
            select c
              from CarEntity c
              join fetch c.brand
              join fetch c.model
              join fetch c.status
             where c.garage.id = :garageId
             order by c.createdAt desc
            """)
    List<CarEntity> findGarageSummary(@Param("garageId") UUID garageId);

    /**
     * Car detail. Pulls every reference row the {@link CarEntity} touches so the response
     * mapping stays inside the open session.
     */
    @Query("""
            select c
              from CarEntity c
              join fetch c.garage
              join fetch c.brand
              join fetch c.model
              join fetch c.drivetrain
              join fetch c.color
              join fetch c.mileageUnit
              join fetch c.fuelType
              join fetch c.status
             where c.id = :id
            """)
    Optional<CarEntity> findDetailById(@Param("id") UUID id);
}

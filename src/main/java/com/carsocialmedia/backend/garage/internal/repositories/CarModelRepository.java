package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link CarModelEntity} read-only reference data.
 *
 * Reference data table (car_models). Lookups only, no creates/updates.
 */
public interface CarModelRepository extends JpaRepository<CarModelEntity, UUID> {
    /**
     * Lists all car models for a specific brand, sorted alphabetically by model name.
     *
     * @param brandId the brand ID
     * @return list of models for the brand in ascending model name order
     */
    List<CarModelEntity> findByBrandIdOrderByModelAsc(UUID brandId);
}

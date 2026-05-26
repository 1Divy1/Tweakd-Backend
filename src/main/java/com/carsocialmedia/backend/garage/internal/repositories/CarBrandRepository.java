package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarBrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link CarBrandEntity} read-only reference data.
 *
 * Reference data table (car_brands). Lookups only, no creates/updates.
 */
public interface CarBrandRepository extends JpaRepository<CarBrandEntity, UUID> {
    /**
     * Lists all car brands, sorted alphabetically by name.
     *
     * @return list of brands in ascending name order
     */
    List<CarBrandEntity> findAllByOrderByNameAsc();
}

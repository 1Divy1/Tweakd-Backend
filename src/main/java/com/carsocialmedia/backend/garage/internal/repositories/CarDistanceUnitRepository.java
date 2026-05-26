package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarDistanceUnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link CarDistanceUnitEntity} read-only reference data.
 *
 * Reference data table (car_distance_units). Lookups only, no creates/updates.
 */
public interface CarDistanceUnitRepository extends JpaRepository<CarDistanceUnitEntity, String> {
    /**
     * Lists all distance units, sorted alphabetically by name.
     *
     * @return list of distance units in ascending name order
     */
    List<CarDistanceUnitEntity> findAllByOrderByNameAsc();
}
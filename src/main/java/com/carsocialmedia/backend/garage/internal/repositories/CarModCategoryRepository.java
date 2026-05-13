package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarModCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link CarModCategoryEntity} read-only reference data.
 *
 * Reference data table (car_mod_categories). Lookups only, no creates/updates.
 */
public interface CarModCategoryRepository extends JpaRepository<CarModCategoryEntity, String> {
    /**
     * Lists all modification categories, sorted alphabetically by modification name.
     *
     * @return list of categories in ascending modification name order
     */
    List<CarModCategoryEntity> findAllByOrderByModNameAsc();
}

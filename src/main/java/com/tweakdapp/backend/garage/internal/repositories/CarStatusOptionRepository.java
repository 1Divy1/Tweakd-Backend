package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarStatusOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link CarStatusOptionEntity} read-only reference data.
 *
 * Reference data table (car_status_options). Lookups only, no creates/updates.
 */
public interface CarStatusOptionRepository extends JpaRepository<CarStatusOptionEntity, String> {
    /**
     * Lists all status options, sorted alphabetically by type.
     *
     * @return list of status options in ascending type order
     */
    List<CarStatusOptionEntity> findAllByOrderByTypeAsc();
}
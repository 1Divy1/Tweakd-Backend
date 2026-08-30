package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarColorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link CarColorEntity} read-only reference data.
 *
 * Reference data table (car_color_options). Lookups only, no creates/updates.
 * Colors include hex codes for frontend rendering.
 */
public interface CarColorRepository extends JpaRepository<CarColorEntity, String> {
    /**
     * Lists all available colors, sorted alphabetically by name.
     *
     * @return list of colors in ascending name order
     */
    List<CarColorEntity> findAllByOrderByNameAsc();
}
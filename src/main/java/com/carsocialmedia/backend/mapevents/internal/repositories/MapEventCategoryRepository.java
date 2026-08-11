package com.carsocialmedia.backend.mapevents.internal.repositories;

import com.carsocialmedia.backend.mapevents.internal.entities.MapEventCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapEventCategoryRepository extends JpaRepository<MapEventCategoryEntity, String> {

    /** Categories offered on the create screen. Unavailable ones stay valid on existing events. */
    List<MapEventCategoryEntity> findByAvailableTrueOrderByLabelAsc();
}

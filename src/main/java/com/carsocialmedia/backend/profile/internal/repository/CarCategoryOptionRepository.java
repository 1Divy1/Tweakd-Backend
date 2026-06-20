package com.carsocialmedia.backend.profile.internal.repository;

import com.carsocialmedia.backend.profile.internal.entity.CarCategoryOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Read-only reference data for {@code car_category_options}.
 */
public interface CarCategoryOptionRepository extends JpaRepository<CarCategoryOptionEntity, String> {
    List<CarCategoryOptionEntity> findAllByOrderByNameAsc();

    /**
     * Counts how many of the given IDs exist. Used to validate a whole selection in one
     * query: if the count differs from the number of distinct requested IDs, at least one
     * is invalid (the service then rejects with {@code InvalidReferenceException}).
     */
    long countByIdIn(Collection<String> ids);
}
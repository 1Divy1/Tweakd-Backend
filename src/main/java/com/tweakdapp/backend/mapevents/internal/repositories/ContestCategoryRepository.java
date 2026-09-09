package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.ContestCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestCategoryRepository extends JpaRepository<ContestCategoryEntity, String> {

    List<ContestCategoryEntity> findByAvailableTrueOrderBySortOrderAsc();
}

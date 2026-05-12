package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarModCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarModCategoryRepository extends JpaRepository<CarModCategoryEntity, String> {
    List<CarModCategoryEntity> findAllByOrderByModNameAsc();
}

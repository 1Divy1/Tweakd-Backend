package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarDistanceUnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarDistanceUnitRepository extends JpaRepository<CarDistanceUnitEntity, String> {
    List<CarDistanceUnitEntity> findAllByOrderByNameAsc();
}
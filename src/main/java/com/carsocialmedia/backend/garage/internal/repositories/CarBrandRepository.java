package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarBrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarBrandRepository extends JpaRepository<CarBrandEntity, UUID> {
    List<CarBrandEntity> findAllByOrderByNameAsc();
}

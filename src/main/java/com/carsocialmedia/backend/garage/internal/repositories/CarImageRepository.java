package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarImageRepository extends JpaRepository<CarImageEntity, UUID> {
    List<CarImageEntity> findByCarIdOrderByDisplayOrderAsc(UUID carId);
}

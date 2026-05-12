package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarModelRepository extends JpaRepository<CarModelEntity, UUID> {
    List<CarModelEntity> findByBrandIdOrderByModelAsc(UUID brandId);
}

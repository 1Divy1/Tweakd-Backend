package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarStatusOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarStatusOptionRepository extends JpaRepository<CarStatusOptionEntity, String> {
    List<CarStatusOptionEntity> findAllByOrderByTypeAsc();
}
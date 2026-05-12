package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarColorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarColorRepository extends JpaRepository<CarColorEntity, String> {
    List<CarColorEntity> findAllByOrderByNameAsc();
}
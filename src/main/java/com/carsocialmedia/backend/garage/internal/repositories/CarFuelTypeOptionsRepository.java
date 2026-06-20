package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarFuelTypeOptionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarFuelTypeOptionsRepository extends JpaRepository<CarFuelTypeOptionsEntity, String> {

    List<CarFuelTypeOptionsEntity> findAllByOrderByNameAsc();
}
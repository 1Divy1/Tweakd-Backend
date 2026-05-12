package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarDrivetrainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarDrivetrainRepository extends JpaRepository<CarDrivetrainEntity, String> {
    List<CarDrivetrainEntity> findAllByOrderByNameAsc();
}
package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarDrivetrainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link CarDrivetrainEntity} read-only reference data.
 *
 * Reference data table (car_drivetrain_options). Lookups only, no creates/updates.
 */
public interface CarDrivetrainRepository extends JpaRepository<CarDrivetrainEntity, String> {
    /**
     * Lists all drivetrain types, sorted alphabetically by name.
     *
     * @return list of drivetrains in ascending name order
     */
    List<CarDrivetrainEntity> findAllByOrderByNameAsc();
}
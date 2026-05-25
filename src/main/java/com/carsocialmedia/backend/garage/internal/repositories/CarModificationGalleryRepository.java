package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarModificationGalleryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CarModificationGalleryRepository extends JpaRepository<CarModificationGalleryEntity, UUID> {

    /** All media for a single modification. */
    List<CarModificationGalleryEntity> findAllByModification_Id(UUID modId);

    /** All media for every modification belonging to a given car (used when building CarDto). */
    @Query("select g from CarModificationGalleryEntity g where g.modification.car.id = :carId")
    List<CarModificationGalleryEntity> findAllByCarId(@Param("carId") UUID carId);
}

package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarGalleryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarGalleryRepository extends JpaRepository<CarGalleryEntity, UUID> {
    List<CarGalleryEntity> findAllByCarIdOrderByPositionAsc(UUID carId);
    void deleteAllByCarId(UUID carId);

    /** Bulk-delete specific gallery items for a car by their R2 keys. */
    void deleteAllByCarIdAndKeyIn(UUID carId, List<String> keys);
}

package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarGalleryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CarGalleryRepository extends JpaRepository<CarGalleryEntity, UUID> {
    List<CarGalleryEntity> findAllByCarIdOrderByPositionAsc(UUID carId);
    void deleteAllByCarId(UUID carId);

    /**
     * The R2 keys of a car's gallery, as plain strings. Deleting a car needs this: loading the rows
     * as entities would leave managed children pointing at the removed car, which Hibernate refuses
     * to flush (TransientPropertyValueException).
     */
    @Query("select g.key from CarGalleryEntity g where g.car.id = :carId")
    List<String> findKeysByCarId(@Param("carId") UUID carId);

    /** Bulk-delete specific gallery items for a car by their R2 keys. */
    void deleteAllByCarIdAndKeyIn(UUID carId, List<String> keys);
}

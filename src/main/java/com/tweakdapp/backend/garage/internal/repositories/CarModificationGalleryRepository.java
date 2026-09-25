package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarModificationGalleryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CarModificationGalleryRepository extends JpaRepository<CarModificationGalleryEntity, UUID> {

    /** All media for a single modification. */
    List<CarModificationGalleryEntity> findAllByModification_Id(UUID modId);

    /** All media for every modification belonging to a given car (used when building CarDto). */
    @Query("select g from CarModificationGalleryEntity g where g.modification.car.id = :carId")
    List<CarModificationGalleryEntity> findAllByCarId(@Param("carId") UUID carId);

    /** R2 keys of every mod's media on a car, without loading entities — see {@code CarGalleryRepository#findKeysByCarId}. */
    @Query("select g.key from CarModificationGalleryEntity g where g.modification.car.id = :carId")
    List<String> findKeysByCarId(@Param("carId") UUID carId);

    /** All media for a batch of modifications, for assembling a page of shared-mod feed cards. */
    @Query("select g from CarModificationGalleryEntity g where g.modification.id in :modIds")
    List<CarModificationGalleryEntity> findAllByModificationIds(@Param("modIds") Collection<UUID> modIds);

    /** Bulk-delete specific media items for a modification by their R2 keys. */
    void deleteAllByModification_IdAndKeyIn(UUID modificationId, List<String> keys);
}

package com.carsocialmedia.backend.garage.internal.repositories;

import com.carsocialmedia.backend.garage.internal.entities.CarModificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CarModificationRepository extends JpaRepository<CarModificationEntity, UUID> {

    @Query("""
            select m
              from CarModificationEntity m
              join fetch m.category
             where m.car.id = :carId
             order by m.installationDate desc, m.createdAt desc
            """)
    List<CarModificationEntity> findByCarIdWithCategory(@Param("carId") UUID carId);
}

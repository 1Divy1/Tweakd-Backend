package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A gallery image attached to a car.
 *
 * The cover and modification before/after images live as columns on their owning rows;
 * this entity backs the {@code car_images} table which holds the variable-length gallery.
 * The createdAt timestamp is DB-managed and read-only from the ORM side.
 */
@Entity
@Table(name = "car_images")
@Getter
@Setter
public class CarImageEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "storage_path", nullable = false, unique = true)
    private String storagePath;

    @Column(name = "display_order")
    private int displayOrder;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
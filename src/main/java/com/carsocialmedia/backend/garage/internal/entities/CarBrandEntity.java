package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A car manufacturer brand (e.g., BMW, Honda, Tesla).
 *
 * Reference data table. Immutable lookup table managed by Supabase. Users select a brand
 * when creating or updating a car, paired with a model from the same brand.
 */
@Entity
@Table(name = "car_brands")
@Getter
@Setter
public class CarBrandEntity {
    /** The brand ID (UUID, PK). */
    @Id
    private UUID id;

    /** The brand name (e.g., "BMW", "Honda"). */
    private String name;

    /** Number of forum threads scoped to this brand. Trigger-maintained by the forums feature; read-only. */
    @Column(name = "thread_count", insertable = false, updatable = false)
    private int threadCount;
}
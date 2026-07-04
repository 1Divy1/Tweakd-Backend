package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A car model produced by a brand (e.g., BMW 3 Series, Honda Civic).
 *
 * Reference data table. Immutable lookup table managed by Supabase. Models are always
 * associated with a brand; client-side form validation must ensure the selected model
 * belongs to the selected brand (server-side consistency check in applyCarRequest).
 */
@Entity
@Table(name = "car_models")
@Getter
@Setter
public class CarModelEntity {
    /** The model ID (UUID, PK). */
    @Id
    private UUID id;

    /** The brand that produces this model (required, FK to car_brands.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id")
    private CarBrandEntity brand;

    /** The model name (e.g., "3 Series", "Civic"). */
    private String model;

    /** Number of forum threads scoped to this model. Trigger-maintained by the forums feature; read-only. */
    @Column(name = "thread_count", insertable = false, updatable = false)
    private int threadCount;
}

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
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * A car a user is dreaming of (wishlist). Belongs to a profile (by {@code profile_id})
 * and references a brand and, optionally, a specific model.
 *
 * <p>Lives in the garage module because it references {@code car_brands}/{@code car_models}
 * (garage-owned reference data); the owning user is stored as a raw {@code profile_id} UUID,
 * mirroring how {@link GarageEntity} stores {@code owner_id}.
 */
@Entity
@Table(name = "dream_cars")
@DynamicUpdate
@Getter
@Setter
public class DreamCarEntity {

    /** The dream car ID (UUID, PK). */
    @Id
    private UUID id;

    /** The UUID of the user who owns this dream car (references profiles.id). */
    @Column(name = "profile_id")
    private UUID profileId;

    /** The dreamed-of car brand (required, FK to car_brands.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id")
    private CarBrandEntity brand;

    /** The specific model (optional, FK to car_models.id, must belong to the brand). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private CarModelEntity model;

    /** When the dream car was added (managed by Supabase, read-only). */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
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
 * A modification (upgrade, customization, or change) made to a car.
 *
 * Tracks when the modification was installed, cost, before/after documentation images,
 * and the mileage at installation. Price is optional — null means not set.
 * The createdAt timestamp is DB-managed and read-only from the ORM side.
 */
@Entity
@DynamicUpdate
@Table(name = "car_modifications")
@Getter
@Setter
public class CarModificationEntity {

    /** The modification ID (UUID, PK). */
    @Id
    private UUID id;

    /** The car this modification was made to (required, FK to cars.id, cascades on delete). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "car_id")
    private CarEntity car;

    /** The modification category (required, FK to car_mod_categories.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private CarModCategoryEntity category;

    /** Short title describing the modification (e.g., "H&R Coilovers"). */
    private String title;

    /** Detailed description of the modification (up to 1000 chars). */
    private String description;

    /** When the modification was installed on the car. */
    @Column(name = "installation_date")
    private Instant installationDate;

    /** The cost of the modification (optional, null if not set). */
    private Integer price;

    /** The car's mileage reading when the modification was installed (nullable). */
    @Column(name = "mileage_at_install")
    private Integer mileageAtInstall;

    /** When this modification record was created (managed by Supabase, read-only). */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}

package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "car_modification_gallery")
@Getter
@Setter
public class CarModificationGalleryEntity {

    @Id
    private UUID id;

    /** FK to the car modifications table */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mod_id")
    private CarModificationEntity modification;

    /** R2 object key. The {@code url} column stores a bucket-relative key, not a full URL. */
    @Column(name = "url")
    private String key;

    @Column(name = "type")
    private String type;

    @Column(name = "phase")
    private String phase;

    /** Managed by Supabase, read-only */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}

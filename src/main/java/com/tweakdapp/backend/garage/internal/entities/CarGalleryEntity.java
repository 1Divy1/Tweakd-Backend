package com.tweakdapp.backend.garage.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "car_gallery")
@DynamicUpdate
@Getter
@Setter
public class CarGalleryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "car_id")
    private CarEntity car;

    /** R2 object key. The {@code url} column stores a bucket-relative key, not a full URL. */
    @Column(name = "url")
    private String key;

    @Column(name = "position")
    private int position;

    /** Managed by Supabase, read-only */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}

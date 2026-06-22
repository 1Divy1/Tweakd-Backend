package com.carsocialmedia.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a post to a car tagged in it. The {@code carId} is a flat reference to a
 * {@code cars} row owned by the garage module.
 */
@Entity
@Table(name = "tagged_cars")
@Getter
@Setter
public class TaggedCarEntity {

    @EmbeddedId
    private TaggedCarId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

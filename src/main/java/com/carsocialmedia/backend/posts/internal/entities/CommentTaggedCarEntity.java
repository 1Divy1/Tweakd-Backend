package com.carsocialmedia.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a comment to a car tagged in it. The {@code carId} is a flat reference to a {@code cars}
 * row owned by the garage module; the car's owner must be tagged in the same comment unless the car
 * belongs to the comment's author (enforced by the service, not the schema).
 */
@Entity
@Table(name = "comment_tagged_cars")
@Getter
@Setter
public class CommentTaggedCarEntity {

    @EmbeddedId
    private CommentTaggedCarId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

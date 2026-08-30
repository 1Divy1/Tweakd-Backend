package com.tweakdapp.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a post to a person (profile) tagged in it. The {@code userId} is a flat reference
 * to a {@code profiles} row owned by the profile module.
 */
@Entity
@Table(name = "tagged_people")
@Getter
@Setter
public class TaggedPersonEntity {

    @EmbeddedId
    private TaggedPersonId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

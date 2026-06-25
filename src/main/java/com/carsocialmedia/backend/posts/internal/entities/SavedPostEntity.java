package com.carsocialmedia.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A post bookmarked ("saved") by a user.
 */
@Entity
@Table(name = "saved_posts")
@Getter
@Setter
public class SavedPostEntity {

    @EmbeddedId
    private SavedPostId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

package com.carsocialmedia.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single image belonging to a post. The {@code image_key} column holds only the
 * Cloudflare R2 object key — the backend builds the full URL before returning it to clients.
 */
@Entity
@Table(name = "post_images")
@Getter
@Setter
public class PostImageEntity {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

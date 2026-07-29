package com.carsocialmedia.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A forum thread topic option.
 * Reference data owned by Supabase; the app only reads it.
 *
 * The {@code id} is a human-readable slug (text PK), not a UUID.
 */
@Entity
@Table(name = "forum_thread_topic_options")
@Getter
@Setter
public class ForumThreadTopicOptionsEntity {

    /** Slug primary key, e.g. {@code "detailing"}. */
    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Optional accent color (hex) for the topic chip; may be {@code null}. */
    @Column(name = "color")
    private String color;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** Number of threads tagged with this topic. Trigger-maintained; read-only. */
    @Column(name = "thread_count", insertable = false, updatable = false)
    private int threadCount;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

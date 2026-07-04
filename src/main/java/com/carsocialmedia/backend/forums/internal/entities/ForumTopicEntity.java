package com.carsocialmedia.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A forum topic — the "topic" axis threads are filtered by (e.g. {@code tuning}, {@code detailing}).
 * Reference data owned by Supabase; the app only reads it.
 *
 * <p>{@code kind} groups topics into {@code component} vs {@code format} buckets for the topic
 * picker. The {@code id} is a human-readable slug (text PK), not a UUID.
 */
@Entity
@Table(name = "forum_topics")
@Getter
@Setter
public class ForumTopicEntity {

    /** Slug primary key, e.g. {@code "tuning"}. */
    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    /** {@code component} | {@code format} — groups topics in the picker. */
    @Column(name = "kind", nullable = false)
    private String kind;

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

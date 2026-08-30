package com.tweakdapp.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A possible feedback lifecycle state (e.g. Submitted / In review / Resolved / Closed). Reference
 * data seeded in Supabase; the app reads it to resolve {@code feedback.status} into a display label
 * plus a {@code color} for rendering a status chip. Moderators advance a feedback through these.
 */
@Entity
@Table(name = "feedback_status_options")
@Getter
@Setter
public class FeedbackStatusOptionEntity {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Ordering hint for displaying the states as a pipeline (Submitted → … → Closed). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Optional hex color for the status chip; nullable. */
    @Column(name = "color")
    private String color;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

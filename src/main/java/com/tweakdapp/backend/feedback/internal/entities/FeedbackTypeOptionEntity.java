package com.tweakdapp.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A predefined feedback category (bug / feature / general). Reference data seeded in Supabase; the
 * app reads it to populate the type picker and links it from {@code feedback.type}.
 */
@Entity
@Table(name = "feedback_type_options")
@Getter
@Setter
public class FeedbackTypeOptionEntity {

    @Id
    private String id;

    @Column(name = "type", nullable = false)
    private String type;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

package com.tweakdapp.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A predefined app feature a user can attach feedback to. Reference data seeded in Supabase; the app
 * reads it to populate the feature picker and links it from {@code feedback.feature}.
 */
@Entity
@Table(name = "feedback_feature_options")
@Getter
@Setter
public class FeedbackFeatureOptionEntity {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

package com.tweakdapp.backend.feedbackfeed.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Seeded reference data: the category a message is filed under — {@code bug},
 * {@code feature_request}, {@code feature_improvement}. {@code type} is the display label.
 */
@Entity
@Table(name = "feedback_feed_feedback_types")
@Getter
@Setter
public class FeedbackFeedTypeOptionEntity {

    @Id
    private String id;

    @Column(name = "type", nullable = false)
    private String type;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

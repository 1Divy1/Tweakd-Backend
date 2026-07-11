package com.carsocialmedia.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's opt-in to in-app notifications about one feedback's status changes. Inserts go through
 * the repository's {@code ON CONFLICT DO NOTHING} native query.
 */
@Entity
@Table(name = "feedback_subscriptions")
@Getter
@Setter
public class FeedbackSubscriptionEntity {

    @EmbeddedId
    private FeedbackSubscriptionId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

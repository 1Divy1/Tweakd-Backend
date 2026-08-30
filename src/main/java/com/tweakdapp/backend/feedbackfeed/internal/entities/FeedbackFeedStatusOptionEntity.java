package com.tweakdapp.backend.feedbackfeed.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Seeded reference data: the roadmap stage of a message — {@code sent},
 * {@code under_development}, {@code completed}. {@code status} is the display label.
 *
 * <p>Unlike the older {@code feedback} module's statuses there is no {@code sort_order} or
 * {@code color} column; the roadmap order is the three constants on
 * {@link FeedbackFeedMessageEntity} and the colouring is the client's.
 */
@Entity
@Table(name = "feedback_feed_status_options")
@Getter
@Setter
public class FeedbackFeedStatusOptionEntity {

    @Id
    private String id;

    @Column(name = "status", nullable = false)
    private String status;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

package com.tweakdapp.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One piece of feedback submitted by a user. {@code userId}, {@code type}, and {@code feature} are
 * flat references (profile UUID / reference-table text ids) rather than JPA associations — the owning
 * {@code profiles} table lives in another module, and the reference tables are read separately, so
 * this avoids lazy-loading across a {@code @Transactional} boundary.
 */
@Entity
@Table(name = "feedback")
@Getter
@Setter
public class FeedbackEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content", nullable = false)
    private String content;

    /** FK to {@code feedback_type_options.id}. */
    @Column(name = "type", nullable = false)
    private String type;

    /** FK to {@code feedback_feature_options.id}; nullable. */
    @Column(name = "feature")
    private String feature;

    @Column(name = "reproduction_steps")
    private String reproductionSteps;

    /** A moderator's reply to the feedback; nullable until one is written. */
    @Column(name = "response")
    private String response;

    /**
     * FK to {@code feedback_status_options.id}. Left out of inserts ({@code insertable = false}) so
     * the column DEFAULT {@code 'submitted'} fires; moderators advance it afterwards.
     */
    @Column(name = "status", insertable = false)
    private String status;

    /** Maintained by Supabase triggers on {@code feedback_votes}; never written by the app. */
    @Column(name = "vote_count", insertable = false, updatable = false)
    private int voteCount;

    /** Maintained by Supabase triggers on {@code feedback_comments}; never written by the app. */
    @Column(name = "comment_count", insertable = false, updatable = false)
    private int commentCount;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

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
 * One user comment under a feedback entry on the public board. {@code feedback.comment_count} is
 * maintained by a Supabase trigger on this table.
 */
@Entity
@Table(name = "feedback_comments")
@Getter
@Setter
public class FeedbackCommentEntity {

    @Id
    private UUID id;

    @Column(name = "feedback_id", nullable = false)
    private UUID feedbackId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content", nullable = false)
    private String content;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

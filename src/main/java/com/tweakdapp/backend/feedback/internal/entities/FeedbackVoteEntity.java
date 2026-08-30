package com.tweakdapp.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One user's upvote on one feedback. Inserts go through the repository's {@code ON CONFLICT DO
 * NOTHING} native query; {@code feedback.vote_count} is maintained by a Supabase trigger.
 */
@Entity
@Table(name = "feedback_votes")
@Getter
@Setter
public class FeedbackVoteEntity {

    @EmbeddedId
    private FeedbackVoteId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

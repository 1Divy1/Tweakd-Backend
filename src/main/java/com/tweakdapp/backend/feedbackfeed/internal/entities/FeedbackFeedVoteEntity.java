package com.tweakdapp.backend.feedbackfeed.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One user's vote on one feedback message: {@code +1} or {@code -1}, enforced by a CHECK constraint.
 * Withdrawing a vote deletes the row; switching direction updates it in place.
 *
 * <p>Two Supabase triggers hang off this table: one keeps
 * {@code feedback_feed_messages.up_votes / down_votes / net_votes} in step, the other rejects any
 * write at all when the target message is {@code completed}. The service checks that status itself
 * first so callers get a clean 409 instead of a raw SQL error.
 */
@Entity
@Table(name = "feedback_feed_votes")
@Getter
@Setter
public class FeedbackFeedVoteEntity {

    public static final short UP = 1;
    public static final short DOWN = -1;

    @EmbeddedId
    private FeedbackFeedVoteId id;

    @Column(name = "vote_type", nullable = false)
    private short voteType;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * DEFAULT now() on insert; nothing in the DB refreshes it, so the service stamps it when a vote
     * switches direction.
     */
    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;
}

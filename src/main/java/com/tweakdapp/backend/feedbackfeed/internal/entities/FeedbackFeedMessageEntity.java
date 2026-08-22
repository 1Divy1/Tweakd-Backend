package com.tweakdapp.backend.feedbackfeed.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One piece of community feedback published to the public feed. {@code authorId}, {@code type} and
 * {@code status} are flat references (a profile UUID / reference-table text ids) rather than JPA
 * associations — {@code profiles} lives in another module and the reference tables are read
 * separately, which keeps lazy loading out of the picture entirely.
 */
@Entity
@Table(name = "feedback_feed_messages")
@Getter
@Setter
public class FeedbackFeedMessageEntity {

    // --- status: feedback_feed_status_options.id ---

    /** Freshly published; the default for every new message. */
    public static final String STATUS_SENT = "sent";

    /** Staff picked it up; the client renders an "in progress" pill on the card. */
    public static final String STATUS_UNDER_DEVELOPMENT = "under_development";

    /**
     * Shipped. Leaves the main feed for the "Completed requests" section, and the DB refuses any
     * further vote against it.
     */
    public static final String STATUS_COMPLETED = "completed";

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "message", nullable = false)
    private String message;

    /** FK to {@code feedback_feed_feedback_types.id} — bug / feature_request / feature_improvement. */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * FK to {@code feedback_feed_status_options.id}. Left out of inserts ({@code insertable = false})
     * so the column DEFAULT {@code 'sent'} fires; staff advance it afterwards.
     */
    @Column(name = "status", insertable = false)
    private String status;

    /** The team's optional official reply, rendered under the author's message. */
    @Column(name = "staff_response_message")
    private String staffResponseMessage;

    /** Maintained by the Supabase trigger on {@code feedback_feed_votes}; never written by the app. */
    @Column(name = "up_votes", insertable = false, updatable = false)
    private int upVotes;

    /** Maintained by the Supabase trigger on {@code feedback_feed_votes}; never written by the app. */
    @Column(name = "down_votes", insertable = false, updatable = false)
    private int downVotes;

    /** {@code up_votes - down_votes}, maintained by the same trigger. Backs the "popular" sort. */
    @Column(name = "net_votes", insertable = false, updatable = false)
    private int netVotes;

    /**
     * Staff removal (spam / abuse) — hides the row from every read while keeping it for audit. An
     * <em>author</em> deleting their own message hard-deletes the row instead, so this stays false
     * for that path. Left out of inserts so the column DEFAULT {@code false} fires.
     */
    @Column(name = "is_deleted", insertable = false)
    private boolean deleted;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * When the message shipped. Stamped (and cleared again if the status moves back out of
     * {@code completed}) by {@code trg_feedback_feed_stamp_completed_at}; never written by the app,
     * so the row must be re-read after a status change to see it.
     */
    @Column(name = "completed_at", insertable = false, updatable = false)
    private Instant completedAt;

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
}

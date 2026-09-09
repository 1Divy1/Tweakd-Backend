package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A car asking to be judged in a contest. Only {@link #ACCEPTED} rows are on the ballot.
 *
 * <p>{@link #votesCount} and {@link #lastVoteAt} are trigger-owned ({@code trg_contest_vote_counts})
 * and never written here; {@link #finalRank} / {@link #finalVotesCount} are written exactly once,
 * by the finaliser.
 */
@Entity
@Table(name = "car_event_contest_entries")
@Getter
@Setter
public class ContestEntryEntity {

    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";
    public static final String WITHDRAWN = "withdrawn";

    @EmbeddedId
    private ContestEntryId id;

    /** Denormalized from the car's garage at insert time. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** DB-managed default; the moment the owner (last) asked. */
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    /** Trigger-owned. */
    @Column(name = "votes_count", insertable = false, updatable = false)
    private int votesCount;

    /** Trigger-owned. The tie-break: on equal counts, the car that reached the count first ranks higher. */
    @Column(name = "last_vote_at", insertable = false, updatable = false)
    private Instant lastVoteAt;

    @Column(name = "final_rank")
    private Short finalRank;

    @Column(name = "final_votes_count")
    private Integer finalVotesCount;

    public boolean isAccepted() {
        return ACCEPTED.equals(status);
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    /** Whether the row still counts as "in" — on the ballot or waiting to be. */
    public boolean isLive() {
        return isAccepted() || isPending();
    }
}

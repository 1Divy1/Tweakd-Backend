package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A contest running inside a car event.
 *
 * <p>{@link #status} is stored and moved only under a row lock, and only by an organizer: they
 * open voting and they finish it. Reads never write it, and nothing moves it on a timer.
 *
 * <p>{@link #opensAt} and {@link #closesAt} are the <em>planned</em> window shown to attendees.
 * They are not enforced — a contest opens when an organizer opens it and closes when they finish
 * it (or when the event itself is marked finished or cancelled).
 *
 * <p>{@link #votesCount} and {@link #entriesCount} are trigger-owned
 * ({@code trg_contest_vote_counts}, {@code trg_contest_entries_count}); never written here.
 */
@Entity
@Table(name = "car_event_contests")
@Getter
@Setter
public class ContestEntity {

    /** Published; entries open; voting locked until an organizer opens it. DB default. */
    public static final String STATUS_SCHEDULED = "scheduled";

    /** Voting. */
    public static final String STATUS_OPEN = "open";

    /** Finalised: {@code final_rank} set on every entry, podium awarded. */
    public static final String STATUS_FINISHED = "finished";

    /** Reserved. Nothing writes it today. */
    public static final String STATUS_CANCELED = "canceled";

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ContestCategoryEntity category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "criteria")
    private String criteria;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "finished_early", nullable = false)
    private boolean finishedEarly;

    /** The organizer who finished it early; null when the clock did. Not an FK. */
    @Column(name = "finished_by")
    private UUID finishedBy;

    /** Trigger-owned. */
    @Column(name = "votes_count", insertable = false, updatable = false)
    private int votesCount;

    /** Trigger-owned. */
    @Column(name = "entries_count", insertable = false, updatable = false)
    private int entriesCount;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isScheduled() {
        return STATUS_SCHEDULED.equals(status);
    }

    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    public boolean isFinished() {
        return STATUS_FINISHED.equals(status);
    }
}

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
 * A <strong>car</strong> entered into an event — the line-up the event page shows, as opposed to
 * the spectators in {@link MapEventAttendeeEntity}.
 *
 * <p>Only the car's owner may enter it, and only before the category's registration deadline. When
 * the event sets {@code requires_participant_approval} the row starts {@link #PENDING} and an
 * organizer moves it to {@link #ACCEPTED} or {@link #REJECTED}; otherwise it is accepted outright.
 *
 * <p>{@link #ownerId} is denormalized from the car's garage so ownership checks and "my
 * registrations" do not need a join; it is filled in from {@code GarageService} at insert time.
 *
 * <p>Inserts, updates and deletes all move {@code car_events.attending_cars_count} via
 * {@code trg_update_car_event_attending_cars_count}, which treats {@link #ACCEPTED} and
 * {@link #WITHDRAWN} as equally "still attending" — the count only moves on a genuine acceptance,
 * rejection, or the hard delete that approving a withdrawal performs.
 */
@Entity
@Table(name = "car_event_participants")
@Getter
@Setter
public class MapEventParticipantEntity {

    /** Awaiting an organizer's decision. DB default. */
    public static final String PENDING = "pending";

    /** In the line-up. */
    public static final String ACCEPTED = "accepted";

    /** Turned down by an organizer. */
    public static final String REJECTED = "rejected";

    /**
     * The owner asked to withdraw and it is awaiting an organizer's decision. The row is
     * <strong>not removed</strong> — the participant is still on the entry list until an organizer
     * approves the request (hard delete) or rejects it (reverts to {@link #ACCEPTED}).
     */
    public static final String WITHDRAWN = "withdrawn";

    @EmbeddedId
    private MapEventParticipantId id;

    /** The {@code profiles} row that owns the car, denormalized from the car's garage. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "status", nullable = false)
    private String status;

    /**
     * The reason given for the most recent withdrawal request. Set when a request is submitted;
     * left as-is on rejection (a historical record of the last attempt), overwritten by the next
     * request.
     */
    @Column(name = "withdraw_note")
    private String withdrawNote;

    /** Why the organizer turned this car down. Set on {@link #REJECTED}, cleared on acceptance. */
    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

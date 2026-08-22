package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The car-meet detail row: the extra fields a {@code car_meet} event has beyond the shared
 * {@link MapEventEntity} columns. One row per event, keyed by the event id.
 *
 * <p>This is the first of what will be one detail table per subcategory.
 */
@Entity
@Table(name = "event_car_meet")
@Getter
@Setter
public class CarMeetEntity {

    /** Shared primary key — the {@code car_events.id} this row details. */
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    /** After this instant no further cars may be entered into the meet. */
    @Column(name = "registration_deadline", nullable = false)
    private Instant registrationDeadline;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

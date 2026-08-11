package com.carsocialmedia.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's RSVP to an event. Attendees are <strong>spectators</strong>, not participants — they
 * come to watch, they do not enter a car (that is {@link MapEventParticipantEntity}).
 *
 * <p>RSVP is open: anyone may mark themselves {@link #ATTENDING} or {@link #INTERESTED} on an
 * approved event, switch between the two, or withdraw. No organizer approval is involved, which is
 * why there is no pending state here.
 *
 * <p>Inserts, updates and deletes all move {@code car_events.attendees_count} via
 * {@code trg_update_car_event_attendees_count}.
 */
@Entity
@Table(name = "car_event_attendees_list")
@Getter
@Setter
public class MapEventAttendeeEntity {

    /** FK value in {@code car_event_attendee_status}: committed to showing up. */
    public static final String ATTENDING = "attending";

    /** FK value in {@code car_event_attendee_status}: might show up. */
    public static final String INTERESTED = "interested";

    @EmbeddedId
    private MapEventAttendeeId id;

    @Column(name = "status", nullable = false)
    private String status;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

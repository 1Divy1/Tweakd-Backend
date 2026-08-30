package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@code public.car_event_attendees_list}: ({@code user_id},
 * {@code event_id}) — one RSVP per user per event.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MapEventAttendeeId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;
}

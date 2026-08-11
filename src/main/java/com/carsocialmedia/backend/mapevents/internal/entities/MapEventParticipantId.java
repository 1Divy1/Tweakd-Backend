package com.carsocialmedia.backend.mapevents.internal.entities;

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
 * Composite primary key for {@code public.car_event_participants}: ({@code event_id},
 * {@code car_id}) — a car can be entered into an event only once.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MapEventParticipantId implements Serializable {

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "car_id")
    private UUID carId;
}

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

/** Composite key of {@code car_event_contest_entries}: a car is on a contest's ballot at most once. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ContestEntryId implements Serializable {

    @Column(name = "contest_id")
    private UUID contestId;

    @Column(name = "car_id")
    private UUID carId;
}

package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A drivetrain type (e.g., FWD, RWD, AWD).
 *
 * Reference data table. Immutable lookup table managed by Supabase. Users select a
 * drivetrain when creating or updating a car.
 */
@Entity
@Table(name = "car_drivetrain_options")
@Getter
@Setter
public class CarDrivetrainEntity {
    /** The drivetrain ID (string, PK, e.g., "fwd", "rwd", "awd"). */
    @Id
    private String id;

    /** The drivetrain name (e.g., "Front-Wheel Drive", "Rear-Wheel Drive"). */
    private String name;
}
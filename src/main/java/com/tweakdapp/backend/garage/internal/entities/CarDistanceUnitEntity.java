package com.tweakdapp.backend.garage.internal.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A distance unit for measuring mileage (e.g., kilometers, miles).
 *
 * Reference data table. Immutable lookup table managed by Supabase. Users select a
 * mileage unit when creating or updating a car to specify how mileage is measured.
 */
@Entity
@Table(name = "car_distance_units")
@Getter
@Setter
public class CarDistanceUnitEntity {
    /** The unit ID (string, PK, e.g., "km", "miles"). */
    @Id
    private String id;

    /** The unit name (e.g., "Kilometers", "Miles"). */
    private String name;
}
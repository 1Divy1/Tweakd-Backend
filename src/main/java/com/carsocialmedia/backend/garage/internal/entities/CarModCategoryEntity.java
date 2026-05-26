package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A category for car modifications/upgrades (e.g., suspension, engine, wheels).
 *
 * Reference data table. Immutable lookup table managed by Supabase. When creating or
 * updating a modification, users select a category to classify the type of work done.
 */
@Entity
@Table(name = "car_mod_categories")
@Getter
@Setter
public class CarModCategoryEntity {
    /** The category ID (string, PK). */
    @Id
    private String id;

    /** The category name (e.g., "Suspension", "Engine", "Exterior", "Interior"). */
    @Column(name = "mod_name")
    private String modName;
}
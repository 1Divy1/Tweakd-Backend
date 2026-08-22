package com.tweakdapp.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A paint or body color option for a car.
 *
 * Reference data table. Immutable lookup table managed by Supabase. Contains hex color
 * codes for frontend rendering. Users select a color when creating or updating a car.
 */
@Entity
@Table(name = "car_color_options")
@Getter
@Setter
public class CarColorEntity {
    /** The color ID (string, PK). */
    @Id
    private String id;

    /** The color name (e.g., "Black", "Deep Blue Pearl"). */
    private String name;

    /** The hex color code for frontend rendering (e.g., "#000000", "#0055CC"). */
    @Column(name = "color_code")
    private String colorCode;
}
package com.tweakdapp.backend.garage.internal.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A car status or role option (e.g., daily driver, project car, collector's piece).
 *
 * Reference data table. Immutable lookup table managed by Supabase. Allows users to
 * categorize their cars by how they use them. Currently not integrated into the car
 * entity but available for future use.
 */
@Entity
@Table(name = "car_status_options")
@Getter
@Setter
public class CarStatusOptionEntity {
    /** The status option ID (string, PK). */
    @Id
    private String id;

    /** The status type name (e.g., "Daily Driver", "Project Car", "Collector"). */
    private String type;
}

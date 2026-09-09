package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A contest category — reference data. {@code custom} is the escape hatch: the organizer types
 * their own title.
 */
@Entity
@Table(name = "car_event_contest_categories")
@Getter
@Setter
public class ContestCategoryEntity {

    public static final String CUSTOM = "custom";

    @Id
    private String id;

    @Column(name = "label", nullable = false)
    private String label;

    /** Glyph key the app maps to a local icon. */
    @Column(name = "icon", nullable = false)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

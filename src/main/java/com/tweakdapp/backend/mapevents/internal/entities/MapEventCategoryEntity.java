package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An event subcategory — reference data. Only {@code car_meet} exists today; more are planned, and
 * each brings its own detail table (the car meet's is {@code event_car_meet}).
 *
 * <p>{@link #available} lets a category be defined before it is offered: an unavailable category is
 * hidden from the create screen but keeps existing events valid.
 */
@Entity
@Table(name = "car_event_categories")
@Getter
@Setter
public class MapEventCategoryEntity {

    /** The only category implemented so far. */
    public static final String CAR_MEET = "car_meet";

    /** Stable slug ({@code car_meet}) — safe to key marker icons off. */
    @Id
    private String id;

    /** Display label ("Car meet"). */
    @Column(name = "category", nullable = false)
    private String label;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

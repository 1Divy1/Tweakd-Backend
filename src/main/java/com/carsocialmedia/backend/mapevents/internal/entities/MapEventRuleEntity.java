package com.carsocialmedia.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One rule set by an event's organizer(s) — table {@code car_event_organizer_rules}, despite the
 * module being about events in general rather than just car meets.
 *
 * <p>Ordered within an event by {@link #sortOrder}; a unique index on {@code (event_id, sort_order)}
 * means a whole-list replace must clear an event's existing rows before inserting the new ones,
 * rather than updating in place.
 */
@Entity
@Table(name = "car_event_organizer_rules")
@Getter
@Setter
public class MapEventRuleEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "rule", nullable = false)
    private String rule;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
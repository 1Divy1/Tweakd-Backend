package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One organizer of an event. Exactly one of {@link #individualOrganizerId} (a {@code profiles} row)
 * and {@link #businessOrganizerId} (a {@code business_accounts} row) is set — a DB CHECK enforces
 * it.
 *
 * <p>The event's creator has a row here too, marked {@link #ROLE_CREATOR}; a partial unique index
 * allows only one per event, and a CHECK requires it to be an individual. Everyone added later is
 * {@link #ROLE_ORGANIZER}.
 *
 * <p><strong>Business organizers are credit-only.</strong> Business accounts have no login yet, so
 * a business is displayed on the event page but can never act; every permission check in this
 * module looks at individual organizers.
 */
@Entity
@Table(name = "car_event_organizers")
@Getter
@Setter
public class MapEventOrganizerEntity {

    /** The user who created the event. At most one per event. */
    public static final String ROLE_CREATOR = "creator";

    /** An organizer added after the fact — an individual or a business. */
    public static final String ROLE_ORGANIZER = "organizer";

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Set when this organizer is an app user; null for a business organizer. */
    @Column(name = "individual_organizer_id")
    private UUID individualOrganizerId;

    /** Set when this organizer is a business account; null for an individual organizer. */
    @Column(name = "business_organizer_id")
    private UUID businessOrganizerId;

    @Column(name = "role", nullable = false)
    private String role;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isCreator() {
        return ROLE_CREATOR.equals(role);
    }
}

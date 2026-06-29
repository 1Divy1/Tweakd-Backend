package com.carsocialmedia.backend.relationships.internal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "follows")
@Getter
@Setter
class RelationshipEntity {

    @EmbeddedId
    private RelationshipId id;

    /**
     * Always {@code accepted} — all accounts are public, so a follow takes effect immediately.
     * The {@code set_follow_initial_status} BEFORE INSERT trigger also forces {@code accepted}.
     */
    private String status;

    // DB-managed: DEFAULT now() in Supabase. Omitting from INSERT lets the default fire.
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}

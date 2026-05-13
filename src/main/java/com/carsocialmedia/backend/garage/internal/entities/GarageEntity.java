package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's garage (collection of cars and modifications).
 *
 * One garage per user. The createdAt is DB-managed (DEFAULT now() in Supabase);
 * it is read-only from the ORM side (@Column insertable/updatable = false).
 */
@Entity
@Table(name = "garages")
@Getter
@Setter
public class GarageEntity {

    /** The garage ID (UUID, PK). */
    @Id
    private UUID id;

    /** The UUID of the user who owns this garage (references profiles.id). */
    @Column(name = "owner_id")
    private UUID ownerId;

    /** When the garage was created (managed by Supabase, read-only). */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}

package com.carsocialmedia.backend.dms.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a DM to a car tagged in it. The {@code carId} is a flat reference to a {@code cars} row
 * owned by the garage module (the car's owner is neither tagged nor notified). Both foreign keys
 * are ON DELETE CASCADE, so deleting the message or the car removes the tag automatically.
 */
@Entity
@Table(name = "dm_message_car_tags")
@Getter
@Setter
public class DmMessageCarTagEntity {

    @EmbeddedId
    private DmMessageCarTagId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

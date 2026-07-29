package com.carsocialmedia.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a thread reply to a car tagged in it. The {@code carId} is a flat reference to a
 * {@code cars} row owned by the garage module; the car's owner must be tagged in the same reply
 * (enforced by the service, not the schema).
 */
@Entity
@Table(name = "forum_thread_reply_tagged_cars")
@Getter
@Setter
public class ForumReplyTaggedCarEntity {

    @EmbeddedId
    private ForumReplyTaggedCarId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

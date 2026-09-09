package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One attendee's vote in one contest. Changing a vote updates {@link #carId} in place, which is
 * what lets the counting trigger move exactly one vote from one entry to another.
 *
 * <p>Never leaves the server: the API exposes counts and the caller's own choice only.
 */
@Entity
@Table(name = "car_event_contest_votes")
@Getter
@Setter
public class ContestVoteEntity {

    @EmbeddedId
    private ContestVoteId id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

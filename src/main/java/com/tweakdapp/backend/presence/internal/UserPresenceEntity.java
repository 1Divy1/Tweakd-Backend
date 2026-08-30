package com.tweakdapp.backend.presence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's persisted last-seen watermark. Deliberately a tiny table of its own (not a column on
 * {@code profiles}) so presence churn never touches the hot profile rows.
 */
@Entity
@Table(name = "user_presence")
@Getter
@Setter
public class UserPresenceEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}

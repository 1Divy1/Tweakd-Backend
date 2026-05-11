package com.carsocialmedia.backend.follow.internal;

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
class FollowEntity {

    @EmbeddedId
    private FollowId id;

    /**
     * {@code accepted} or {@code pending}. Initial value is set by the
     * {@code set_follow_initial_status} BEFORE INSERT trigger in Supabase
     * (based on the target's {@code is_private} flag), so the app should not
     * pre-fill it on insert.
     */
    private String status;

    // DB-managed: DEFAULT now() in Supabase. Omitting from INSERT lets the default fire.
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}

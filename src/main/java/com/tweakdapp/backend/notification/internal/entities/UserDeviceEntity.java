package com.tweakdapp.backend.notification.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One registered device: the FCM registration token a push is addressed to, plus the account that
 * currently owns it. {@code token} is unique on its own (not paired with {@code userId}), which is
 * what makes device handoff work — re-registering a token reassigns the row to the new user, so the
 * previous account stops receiving that device's pushes immediately.
 *
 * <p>This table is the single source of truth for the user↔token relationship. The Supabase edge
 * function that sends {@code dm} pushes reads the same rows.
 *
 * <p>{@code token} is a device-addressable secret and must never leave the backend: no endpoint
 * returns it, and it is never written to a log in full.
 */
@Entity
@Table(name = "user_devices_firebase_token")
@Getter
@Setter
public class UserDeviceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, updatable = false)
    private String token;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "locale")
    private String locale;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** DB-managed: bumped by the {@code user_devices_firebase_token_touch} trigger on every update. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}

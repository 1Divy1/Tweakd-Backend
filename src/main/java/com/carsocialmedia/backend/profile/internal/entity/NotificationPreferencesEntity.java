package com.carsocialmedia.backend.profile.internal.entity;

import com.carsocialmedia.backend.profile.dto.NotificationPreferencesDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's notification toggles. One row per profile ({@code profile_id} is the PK
 * and FK to {@code profiles.id}). All flags default to {@code true} in Supabase.
 */
@Entity
@DynamicUpdate
@Table(name = "notification_preferences")
@Getter
@Setter
public class NotificationPreferencesEntity {

    @Id
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "likes_enabled", nullable = false)
    private boolean likesEnabled = true;

    @Column(name = "comments_enabled", nullable = false)
    private boolean commentsEnabled = true;

    @Column(name = "shares_enabled", nullable = false)
    private boolean sharesEnabled = true;

    @Column(name = "dms_enabled", nullable = false)
    private boolean dmsEnabled = true;

    @Column(name = "flash_meets_enabled", nullable = false)
    private boolean flashMeetsEnabled = true;

    @Column(name = "organized_events_enabled", nullable = false)
    private boolean organizedEventsEnabled = true;

    /** Reminders for scheduled services and expiring documents (e.g. service book) on your cars. */
    @Column(name = "service_reminders_enabled", nullable = false)
    private boolean serviceRemindersEnabled = true;

    /** Notifications for being tagged (or having a car tagged) in forum threads and replies. */
    @Column(name = "tags_enabled", nullable = false)
    private boolean tagsEnabled = true;

    /**
     * Notifications for running a map event you organize (a car registered, you were added as an
     * organizer). Distinct from {@link #organizedEventsEnabled}, which covers attendee-facing
     * event logistics.
     */
    @Column(name = "event_organizer_enabled", nullable = false)
    private boolean eventOrganizerEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationPreferencesDto toDto() {
        return new NotificationPreferencesDto(
                likesEnabled, commentsEnabled, sharesEnabled,
                dmsEnabled, flashMeetsEnabled, organizedEventsEnabled, serviceRemindersEnabled,
                tagsEnabled, eventOrganizerEnabled
        );
    }
}
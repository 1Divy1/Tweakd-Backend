package com.tweakdapp.backend.profile.internal.repository;

import com.tweakdapp.backend.profile.internal.entity.NotificationPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Per-profile notification toggles ({@code notification_preferences}). Keyed by
 * {@code profile_id}.
 */
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferencesEntity, UUID> {
}
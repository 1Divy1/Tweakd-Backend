package com.carsocialmedia.backend.profile.dto;

/**
 * A user's notification toggles.
 */
public record NotificationPreferencesDto(
        boolean likesEnabled,
        boolean commentsEnabled,
        boolean sharesEnabled,
        boolean dmsEnabled,
        boolean flashMeetsEnabled,
        boolean organizedEventsEnabled,
        boolean priceDropsEnabled
) {}
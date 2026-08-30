package com.tweakdapp.backend.profile.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Replace-all update of a user's notification toggles. Every flag must be supplied.
 */
public record NotificationPreferencesRequest(
        @NotNull(message = "likesEnabled is required") Boolean likesEnabled,
        @NotNull(message = "commentsEnabled is required") Boolean commentsEnabled,
        @NotNull(message = "sharesEnabled is required") Boolean sharesEnabled,
        @NotNull(message = "dmsEnabled is required") Boolean dmsEnabled,
        @NotNull(message = "flashMeetsEnabled is required") Boolean flashMeetsEnabled,
        @NotNull(message = "organizedEventsEnabled is required") Boolean organizedEventsEnabled,
        @NotNull(message = "serviceRemindersEnabled is required") Boolean serviceRemindersEnabled,
        @NotNull(message = "tagsEnabled is required") Boolean tagsEnabled,
        @NotNull(message = "eventOrganizerEnabled is required") Boolean eventOrganizerEnabled
) {}
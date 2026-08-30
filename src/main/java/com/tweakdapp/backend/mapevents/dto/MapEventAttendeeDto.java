package com.tweakdapp.backend.mapevents.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

/**
 * One entry in an event's attendee list — a spectator, not a car in the line-up.
 *
 * @param profile the attendee (id, username, avatar)
 * @param status  {@code attending} or {@code interested}
 */
public record MapEventAttendeeDto(
        ProfileSearchResultDto profile,
        String status
) {}

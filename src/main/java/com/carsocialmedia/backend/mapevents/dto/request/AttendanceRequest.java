package com.carsocialmedia.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * The caller's RSVP to an event. Attendees are spectators — no organizer approval is involved, so
 * this takes effect immediately and can be changed or withdrawn while the event is still running.
 *
 * @param status {@code attending} or {@code interested}
 */
public record AttendanceRequest(
        @NotBlank String status
) {}

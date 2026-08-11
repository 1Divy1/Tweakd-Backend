package com.carsocialmedia.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * An organizer's verdict on a car that was entered into their event.
 *
 * @param status {@code accepted} or {@code rejected}
 */
public record ParticipantDecisionRequest(
        @NotBlank String status
) {}

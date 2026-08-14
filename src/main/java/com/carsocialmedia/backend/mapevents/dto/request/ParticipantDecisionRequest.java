package com.carsocialmedia.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An organizer's verdict on a car that was entered into their event.
 *
 * @param status {@code accepted} or {@code rejected}
 * @param reason required when rejecting, so the owner knows why; ignored when accepting
 */
public record ParticipantDecisionRequest(
        @NotBlank String status,
        @Size(max = 1000) String reason
) {}

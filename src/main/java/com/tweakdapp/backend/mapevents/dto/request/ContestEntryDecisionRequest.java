package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An organizer's verdict on a car's request to enter a contest.
 *
 * @param status {@code accepted} or {@code rejected}
 * @param reason required when rejecting; ignored when accepting
 */
public record ContestEntryDecisionRequest(
        @NotBlank String status,
        @Size(max = 300) String reason
) {}

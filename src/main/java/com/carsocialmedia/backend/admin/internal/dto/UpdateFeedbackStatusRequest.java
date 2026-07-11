package com.carsocialmedia.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;

/** Moves a feedback to a new lifecycle status ({@code feedback_status_options} id). */
public record UpdateFeedbackStatusRequest(
        @NotBlank String status) {
}

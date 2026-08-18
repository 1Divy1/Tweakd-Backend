package com.carsocialmedia.backend.feedbackfeed.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Moving a message along the roadmap.
 *
 * @param status a {@code feedback_feed_status_options.id} — sent / under_development / completed
 */
public record UpdateFeedbackFeedStatusRequest(

        @NotBlank(message = "A status is required")
        String status
) {}

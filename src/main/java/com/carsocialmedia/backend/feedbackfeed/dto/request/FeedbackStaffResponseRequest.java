package com.carsocialmedia.backend.feedbackfeed.dto.request;

import jakarta.validation.constraints.Size;

/**
 * The team's official reply to a feedback message, shown under the author's text.
 *
 * <p>The response is optional by design, so {@code null} or blank is a legitimate payload: it
 * clears any reply already written.
 */
public record FeedbackStaffResponseRequest(

        @Size(max = 1000, message = "A response cannot exceed 1000 characters")
        String response
) {}

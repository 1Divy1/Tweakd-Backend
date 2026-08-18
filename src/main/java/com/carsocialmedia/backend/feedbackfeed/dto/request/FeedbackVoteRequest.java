package com.carsocialmedia.backend.feedbackfeed.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Casting a vote. {@code value} is {@code 1} (up) or {@code -1} (down); anything else is rejected.
 *
 * <p>The endpoint is a toggle: sending the direction the caller already holds withdraws the vote,
 * the opposite direction switches it.
 */
public record FeedbackVoteRequest(

        @NotNull(message = "A vote value is required")
        Integer value
) {}

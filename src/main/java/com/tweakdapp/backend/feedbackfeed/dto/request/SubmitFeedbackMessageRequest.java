package com.tweakdapp.backend.feedbackfeed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Publishing a message to the community feed. The 500-character ceiling matches the compose
 * screen's counter.
 *
 * @param type    a {@code feedback_feed_feedback_types.id} — bug / feature_request /
 *                feature_improvement
 * @param message the feedback text
 */
public record SubmitFeedbackMessageRequest(

        @NotBlank(message = "A category is required")
        String type,

        @NotBlank(message = "A message is required")
        @Size(max = 500, message = "Feedback cannot exceed 500 characters")
        String message
) {}

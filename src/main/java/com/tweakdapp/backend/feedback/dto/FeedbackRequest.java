package com.tweakdapp.backend.feedback.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for submitting feedback. The submitting user comes from the JWT, not the body.
 *
 * @param content           the free-text feedback message (required)
 * @param type              a {@code feedback_type_options} id — {@code bug} / {@code feature} /
 *                          {@code general} (required)
 * @param feature           a {@code feedback_feature_options} id the feedback is about, or
 *                          {@code null} if it isn't tied to a specific listed feature
 * @param reproductionSteps optional steps to reproduce a bug; the mobile client only collects this
 *                          for the {@code bug} type, but the backend accepts it for any type
 */
public record FeedbackRequest(
        @NotBlank(message = "content must not be blank") String content,
        @NotBlank(message = "type must not be blank") String type,
        String feature,
        String reproductionSteps) {
}

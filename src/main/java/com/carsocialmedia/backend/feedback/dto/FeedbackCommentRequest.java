package com.carsocialmedia.backend.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for commenting on a feedback entry. */
public record FeedbackCommentRequest(
        @NotBlank @Size(max = 2000) String content
) {}

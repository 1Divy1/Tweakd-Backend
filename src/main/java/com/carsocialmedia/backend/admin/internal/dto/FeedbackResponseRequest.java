package com.carsocialmedia.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The team's official response shown on the public feedback board. */
public record FeedbackResponseRequest(
        @NotBlank @Size(max = 2000) String response) {
}

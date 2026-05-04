package com.carsocialmedia.backend.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OnboardingRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(regexp = "^[a-z0-9._]+$", message = "Username may only contain letters, numbers, '.' and '_'")
        String username,

        @Size(max = 500, message = "Bio must be at most 500 characters")
        String bio
) {}
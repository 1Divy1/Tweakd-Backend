package com.tweakdapp.backend.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * First-time onboarding payload. The username, city, and discovery radius are all
 * required; only the bio is optional. The region and country are derived from the
 * selected {@code cityId} (each city belongs to exactly one region, and each region
 * to one country), so they are not sent here.
 */
public record OnboardingRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(regexp = "^[a-z](?!.*[_.]{2})[a-z0-9._]*[a-z0-9]$", message = "Username may only contain letters, numbers, '.' and '_'")
        String username,

        @Size(max = 500, message = "Bio must be at most 500 characters")
        String bio,

        @NotBlank
        String cityId,

        @NotNull
        @Min(value = 1, message = "Discovery radius must be between 1 and 100 km")
        @Max(value = 100, message = "Discovery radius must be between 1 and 100 km")
        Integer discoveryRadiusKm
) {
        public OnboardingRequest {
                if (bio == null) {
                        bio = "";
                }
        }
}
package com.carsocialmedia.backend.profile.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/profile/me} — a partial profile edit. Both fields are optional:
 * an absent/null field leaves the stored value unchanged, an empty string clears it. {@code name}
 * is trimmed; {@code name} and {@code bio} are length-capped and reject over-limit input with 400.
 *
 * @param name the display name (trimmed, max 80 chars, may be blank to clear), or {@code null} to leave unchanged
 * @param bio  the profile bio (max 500 chars, may be blank to clear), or {@code null} to leave unchanged
 */
public record ProfileEditRequest(

        @Size(max = 80, message = "Name must be at most 80 characters")
        String name,

        @Size(max = 500, message = "Bio must be at most 500 characters")
        String bio
) {
    public ProfileEditRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}

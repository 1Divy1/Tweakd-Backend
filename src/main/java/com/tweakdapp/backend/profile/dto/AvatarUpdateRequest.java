package com.tweakdapp.backend.profile.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code PATCH /api/v1/profile/me/avatar}. The {@code key} is the R2 object key returned
 * by the avatar upload-URL endpoint, persisted to {@code profiles.avatar_url}. Blank → 400.
 */
public record AvatarUpdateRequest(
        @NotBlank(message = "key is required")
        String key
) {}

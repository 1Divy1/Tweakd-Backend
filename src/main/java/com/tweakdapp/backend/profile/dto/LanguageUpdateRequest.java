package com.tweakdapp.backend.profile.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code PATCH /api/v1/profile/me/language}. The {@code languageId} is a code from
 * {@code app_language_options} (e.g. {@code "en"}, {@code "ro"}); an unknown code is rejected 400.
 */
public record LanguageUpdateRequest(
        @NotBlank(message = "language_id is required")
        String languageId
) {}

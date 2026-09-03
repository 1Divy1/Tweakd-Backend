package com.tweakdapp.backend.badges.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The dashboard's write shape for a badge definition — the body of both the create and the edit.
 *
 * <p>The two url fields are <strong>R2 object keys</strong> within the {@code app-assets} bucket
 * ({@code badges/pioneer/badge-unlocked.svg}), not URLs. That is the same contract the column has,
 * and it is what keeps the bucket's domain a config value; the pattern rejects a full URL outright
 * rather than letting it through to produce a doubled prefix that 404s silently in the app.
 *
 * @param title       display name; required
 * @param description what it takes to unlock it; optional
 * @param unlockedKey R2 key of the earned artwork; required
 * @param lockedKey   R2 key of the not-yet-earned artwork; optional — omit it and the client greys
 *                    out the unlocked artwork itself
 * @param available   whether it may be awarded and appears in the catalogue
 */
public record BadgeUpsertRequest(
        @NotBlank @Size(max = 80) String title,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 300)
        @Pattern(regexp = "^(?!https?://).+$", message = "must be an R2 object key, not a URL")
        String unlockedKey,
        @Size(max = 300)
        @Pattern(regexp = "^(?!https?://).+$", message = "must be an R2 object key, not a URL")
        String lockedKey,
        boolean available
) {}

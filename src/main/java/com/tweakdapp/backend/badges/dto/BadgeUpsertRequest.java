package com.tweakdapp.backend.badges.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

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
 * @param awardTrigger the event that unlocks this badge automatically, as a
 *                     {@link com.tweakdapp.backend.badges.BadgeTrigger#code() trigger code}.
 *                     Optional; omit it for a badge staff hand out themselves, which is the normal
 *                     case. An unrecognised code is rejected rather than stored — the failure it
 *                     would otherwise cause is a badge that silently never unlocks, which nobody
 *                     would notice for weeks
 * @param earnableFrom start of the window in which the trigger pays out, inclusive; optional.
 *                     Setting it in the future stages a badge that starts awarding on its own
 * @param earnableUntil end of that window, exclusive; optional. This is how a limited-time badge
 *                      withdraws itself on its date — the users already holding it keep it
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
        boolean available,
        String awardTrigger,
        Instant earnableFrom,
        Instant earnableUntil
) {}

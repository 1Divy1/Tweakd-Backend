package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Attaches an uploaded cover image to an event: the R2 object key returned by
 * {@code GET /api/storage/events/{eventId}/cover}, sent back once the upload has completed.
 *
 * @param key the R2 object key, which must live under this event's {@code events/{eventId}/} prefix
 */
public record CoverImageKeyRequest(
        @NotBlank String key
) {}

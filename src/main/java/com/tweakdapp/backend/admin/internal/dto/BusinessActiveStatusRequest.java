package com.tweakdapp.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Takes a verified business off the map ({@code suspended}) or puts it back ({@code active}).
 * Separate from verification on purpose: suspending a business for behaviour is not the same
 * decision as deciding it is not who it claims to be, and the two statuses are stored separately.
 *
 * @param activeStatus {@code active} or {@code suspended}. {@code deleted} is rejected — nothing in
 *                     the app can undo it, so it stays a deliberate database operation
 */
public record BusinessActiveStatusRequest(
        @NotBlank String activeStatus
) {}

package com.tweakdapp.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A reviewer's reason for turning down a submitted map event. Required — the creator sees this text
 * and is expected to act on it, since editing a rejected event resubmits it.
 *
 * @param reason why the event was rejected
 */
public record EventRejectionRequest(
        @NotBlank @Size(max = 1000) String reason
) {}

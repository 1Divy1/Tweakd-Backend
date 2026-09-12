package com.tweakdapp.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A reviewer's reason for turning down a business's verification. Required: a refusal the operator
 * cannot act on is worse than none, and this text is the only record of why.
 */
public record BusinessRejectionRequest(
        @NotBlank @Size(max = 500) String reason
) {}

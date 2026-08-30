package com.tweakdapp.backend.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for opening a support ticket: the subject line, a category id from
 * {@code GET /support/categories}, and the first message of the conversation. Priority is not
 * client-settable — admins triage it in the dashboard.
 */
public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank String category,
        @NotBlank @Size(max = 5000) String message
) {}

package com.carsocialmedia.backend.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for replying inside a ticket (used by both the app and the admin dashboard). */
public record TicketMessageRequest(
        @NotBlank @Size(max = 5000) String content
) {}

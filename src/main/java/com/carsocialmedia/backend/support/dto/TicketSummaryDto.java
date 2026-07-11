package com.carsocialmedia.backend.support.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a ticket list (the app's "my tickets" and the dashboard's queue): headline data plus a
 * preview of the latest message, without loading the whole conversation.
 */
public record TicketSummaryDto(
        UUID id,
        String subject,
        String category,
        String priority,
        String status,
        ProfileSearchResultDto requester,
        boolean business,
        String lastMessagePreview,
        Instant createdAt,
        Instant lastMessageAt
) {}

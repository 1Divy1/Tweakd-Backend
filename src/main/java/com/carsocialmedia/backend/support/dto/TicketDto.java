package com.carsocialmedia.backend.support.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One support ticket with its full conversation, oldest message first. {@code business} is derived
 * from the requester's {@code profiles.is_business}; {@code assignee} is the staff member the
 * dashboard assigned ({@code null} = unassigned). Status: {@code open} = waiting on staff,
 * {@code awaiting_user} = staff replied last, {@code resolved} = closed.
 */
public record TicketDto(
        UUID id,
        String subject,
        String category,
        String priority,
        String status,
        ProfileSearchResultDto requester,
        boolean business,
        ProfileSearchResultDto assignee,
        List<TicketMessageDto> messages,
        Instant createdAt,
        Instant lastMessageAt
) {}

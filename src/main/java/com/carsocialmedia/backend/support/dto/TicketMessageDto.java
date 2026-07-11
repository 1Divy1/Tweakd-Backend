package com.carsocialmedia.backend.support.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/** One message in a ticket's conversation. {@code staff} marks replies written from the dashboard. */
public record TicketMessageDto(
        UUID id,
        ProfileSearchResultDto sender,
        boolean staff,
        String content,
        Instant createdAt
) {}

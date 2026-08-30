package com.tweakdapp.backend.support.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.shared.staff.StaffRefDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in a ticket's conversation. At most one of the sender fields is set, keyed by
 * {@code staff}: dashboard replies carry {@code staffSender} (staff have no app profile),
 * requester messages carry {@code sender}.
 */
public record TicketMessageDto(
        UUID id,
        ProfileSearchResultDto sender,
        StaffRefDto staffSender,
        boolean staff,
        String content,
        Instant createdAt
) {}

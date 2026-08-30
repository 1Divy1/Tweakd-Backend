package com.tweakdapp.backend.support.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.shared.staff.StaffRefDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One support ticket with its full conversation, oldest message first. {@code business} is derived
 * from the requester's {@code profiles.is_business}; {@code assignee} is the staff member the
 * dashboard assigned ({@code null} = unassigned) — staff are not app users, so it is a staff ref,
 * not a profile. Status: {@code open} = waiting on staff, {@code awaiting_user} = staff replied
 * last, {@code resolved} = closed.
 */
public record TicketDto(
        UUID id,
        String subject,
        String category,
        String priority,
        String status,
        ProfileSearchResultDto requester,
        boolean business,
        StaffRefDto assignee,
        List<TicketMessageDto> messages,
        Instant createdAt,
        Instant lastMessageAt
) {}

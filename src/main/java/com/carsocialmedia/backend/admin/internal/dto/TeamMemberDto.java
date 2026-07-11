package com.carsocialmedia.backend.admin.internal.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of the Moderators page: team data joined with the member's public profile card. */
public record TeamMemberDto(
        UUID userId,
        String username,
        String avatarUrl,
        String role,
        String status,
        Instant joinedAt,
        Instant lastActiveAt) {
}

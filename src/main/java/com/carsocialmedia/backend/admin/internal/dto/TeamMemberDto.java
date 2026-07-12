package com.carsocialmedia.backend.admin.internal.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of the Moderators page. Staff identity comes from the team row itself, not a profile. */
public record TeamMemberDto(
        UUID userId,
        String email,
        String displayName,
        String avatarUrl,
        String role,
        String status,
        Instant joinedAt,
        Instant lastActiveAt) {
}

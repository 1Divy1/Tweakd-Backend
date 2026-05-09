package com.carsocialmedia.backend.follow;

import java.time.Instant;
import java.util.UUID;

public record FollowRequestDto(
        UUID profileId,
        String username,
        String avatarUrl,
        Instant requestedAt
) {}

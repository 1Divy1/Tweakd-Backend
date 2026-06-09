package com.carsocialmedia.backend.follow.dto;

import java.util.UUID;

public record FollowProfileSearchResult(
        UUID id,
        String username,
        String avatarUrl,
        boolean isFollowing
) {}

package com.carsocialmedia.backend.profile;

import java.util.UUID;

public record PublicProfileDto(
        UUID id,
        String name,
        String username,
        String avatarUrl,
        String bio,
        String externalLink,
        int followersCount,
        int followingCount,
        boolean isVerified,
        boolean isBusiness
) {}

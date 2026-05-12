package com.carsocialmedia.backend.profile.dto;

import java.util.UUID;

public record ProfileDto(
        UUID id,
        String role,
        String name,
        String username,
        String avatarUrl,
        String bio,
        String externalLink,
        int followersCount,
        int followingCount,
        boolean isVerified,
        boolean isBusiness,
        boolean isPrivate,
        boolean requiresOnboarding
) {}

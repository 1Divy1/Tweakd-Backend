package com.tweakdapp.backend.profile.dto;

import com.tweakdapp.backend.badges.dto.UserBadgeDto;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own profile.
 *
 * @param badges the badges this user has unlocked, newest first — embedded rather than fetched
 *               separately so the profile screen paints its badge row in the same round trip as the
 *               header. Empty, never null. The badges still <em>locked</em> are a separate read
 *               ({@code GET /api/v1/badges/me/locked}): that list grows with the catalogue and is
 *               only needed when the user opens that section.
 */
public record ProfileDto(
        UUID id,
        String name,
        String username,
        String avatarUrl,
        String bio,
        String externalLink,
        int followersCount,
        int followingCount,
        boolean isVerified,
        boolean isBusiness,
        boolean requiresOnboarding,
        int reputationScore,
        String cityId,
        Integer discoveryRadiusKm,
        String appLanguage,
        List<UserBadgeDto> badges
) {}

package com.tweakdapp.backend.profile.dto;

import com.tweakdapp.backend.badges.dto.UserBadgeDto;

import java.util.List;
import java.util.UUID;

/**
 * Someone else's profile.
 *
 * @param badges the badges this user has unlocked, newest first — embedded so a visited profile
 *               renders its badge row with the header rather than a beat later. Empty, never null.
 *               Only earned badges: what a stranger has left to unlock is not the viewer's business,
 *               which is why there is no public equivalent of the locked list.
 */
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
        boolean isBusiness,
        int reputationScore,
        List<UserBadgeDto> badges
) {}

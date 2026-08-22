package com.tweakdapp.backend.profile.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The author panel of a moderation case: who the reported user is, how established their account is
 * ({@code createdAt} → "account age", {@code followersCount}), and their current ban state. Consumed
 * by the {@code admin} module.
 */
public record ProfileModerationSnapshotDto(
        UUID id,
        String username,
        String name,
        String avatarUrl,
        long followersCount,
        boolean business,
        boolean banned,
        Instant bannedUntil,
        Instant createdAt
) {}

package com.tweakdapp.backend.relationships.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of the caller's blocked-accounts list. */
public record BlockedAccountDto(
        UUID id,
        String name,
        String username,
        String avatarUrl,
        Instant blockedAt
) {}

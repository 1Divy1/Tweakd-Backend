package com.tweakdapp.backend.relationships.dto;

import java.util.UUID;

public record FollowProfileSearchResult(
        UUID id,
        String username,
        String avatarUrl,
        boolean isFollowing
) {}

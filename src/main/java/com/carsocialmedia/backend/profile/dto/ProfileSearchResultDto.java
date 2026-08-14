package com.carsocialmedia.backend.profile.dto;

import java.util.UUID;

public record ProfileSearchResultDto(
        UUID id,
        String name,
        String username,
        String avatarUrl
) {}

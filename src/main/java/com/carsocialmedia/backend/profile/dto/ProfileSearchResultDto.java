package com.carsocialmedia.backend.profile.dto;

import java.util.UUID;

public record ProfileSearchResultDto(
        UUID id,
        String username,
        String avatarUrl
) {}

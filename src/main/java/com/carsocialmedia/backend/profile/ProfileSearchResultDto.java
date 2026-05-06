package com.carsocialmedia.backend.profile;

import java.util.UUID;

public record ProfileSearchResultDto(
        UUID id,
        String username,
        String avatarUrl
) {}

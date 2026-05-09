package com.carsocialmedia.backend.profile;

import jakarta.validation.constraints.NotNull;

public record PrivacyRequest(
        @NotNull(message = "isPrivate is required")
        Boolean isPrivate
) {}

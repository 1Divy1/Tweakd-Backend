package com.carsocialmedia.backend.feedbackfeed.dto;

import java.util.UUID;

/**
 * The author card on a feed item. {@code name} (the display name) and {@code username} (the handle
 * the card actually shows) are separate fields and neither ever holds the other's value.
 */
public record FeedbackAuthorDto(
        UUID id,
        String name,
        String username,
        String avatarUrl
) {}

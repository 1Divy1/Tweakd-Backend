package com.carsocialmedia.backend.feedback.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One comment under a feedback entry. {@code mine} tells the client whether to offer its delete
 * affordance without comparing ids itself.
 */
public record FeedbackCommentDto(
        UUID id,
        ProfileSearchResultDto author,
        String content,
        boolean mine,
        Instant createdAt
) {}

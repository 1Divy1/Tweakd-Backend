package com.carsocialmedia.backend.feedback.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One feedback entry as the admin dashboard sees it: the board card plus the admin-only fields
 * ({@code reproductionSteps}). Consumed by the {@code admin} module.
 */
public record AdminFeedbackDto(
        UUID id,
        String content,
        String type,
        String feature,
        FeedbackStatusDto status,
        ProfileSearchResultDto author,
        long voteCount,
        long commentCount,
        String response,
        String reproductionSteps,
        Instant createdAt
) {}

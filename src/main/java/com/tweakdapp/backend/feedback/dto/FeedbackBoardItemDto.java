package com.tweakdapp.backend.feedback.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One feedback entry on the public feedback board / roadmap. {@code type} and {@code feature} are
 * resolved to display labels; {@code response} is the team's official reply ({@code null} until one
 * exists). The two viewer flags personalize the card for the requesting user.
 */
public record FeedbackBoardItemDto(
        UUID id,
        String content,
        String type,
        String feature,
        FeedbackStatusDto status,
        ProfileSearchResultDto author,
        long voteCount,
        long commentCount,
        boolean viewerHasVoted,
        boolean viewerIsSubscribed,
        String response,
        Instant createdAt
) {}

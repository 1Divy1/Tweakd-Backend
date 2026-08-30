package com.tweakdapp.backend.admin.internal.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One moderation-queue row. {@code contentPreview} is a truncated snapshot of the reported thing
 * (or {@code "[deleted]"} when the author removed it while the case was open); {@code author} is
 * {@code null} in that case too. {@code severity} is the LOW / MEDIUM / HIGH heuristic from report
 * count + reason.
 */
public record CaseSummaryDto(
        long id,
        String targetType,
        UUID targetId,
        String status,
        String severity,
        long reportCount,
        String contentPreview,
        ProfileSearchResultDto author,
        Instant createdAt,
        Instant lastReportedAt) {
}

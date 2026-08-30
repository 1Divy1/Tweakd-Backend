package com.tweakdapp.backend.admin.internal.dto;

import com.tweakdapp.backend.profile.dto.ProfileModerationSnapshotDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.shared.staff.StaffRefDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full case view: the reported content, its author's moderation panel (reach, account age,
 * ban state), every report against it, and the action history. {@code content} / {@code mediaUrls}
 * / {@code contentCreatedAt} and {@code author} are {@code null} when the content vanished while
 * the case was open (author self-delete).
 *
 * @param reports newest first
 * @param actions oldest first (a timeline)
 */
public record CaseDetailDto(
        long id,
        String targetType,
        UUID targetId,
        String status,
        String resolution,
        String severity,
        String content,
        List<String> mediaUrls,
        Instant contentCreatedAt,
        ProfileModerationSnapshotDto author,
        List<CaseReportDto> reports,
        List<CaseActionDto> actions,
        Instant createdAt,
        Instant lastReportedAt,
        Instant resolvedAt,
        StaffRefDto resolvedBy) {

    /** One report against the case's target, with the reporter's profile card resolved. */
    public record CaseReportDto(
            ProfileSearchResultDto reporter,
            String reason,
            String status,
            Instant createdAt) {
    }

    /** One audit-log entry of the case. The moderator is staff, not an app user. */
    public record CaseActionDto(
            String action,
            StaffRefDto moderator,
            String note,
            Instant createdAt) {
    }
}

package com.tweakdapp.backend.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One report filed against a specific target, for the admin moderation view ("who reported this
 * and why"). The counterpart of {@link MyReportDto}: that one is scoped to a reporter, this one to
 * a target.
 *
 * <p>Self-contained on purpose — the reporter's username/avatar is resolved by the admin module
 * through the profile module, not here, to keep this module a leaf.
 *
 * @param reporterId the reporting user's profile UUID
 * @param reason     the human-readable preset reason text, or {@code null} if none was picked
 * @param status     moderation status: {@code "pending"}, {@code "in_progress"}, {@code "resolved"},
 *                   or {@code "dismissed"}
 * @param createdAt  when the report was filed
 */
public record TargetReportDto(
        UUID reporterId,
        String reason,
        String status,
        Instant createdAt) {
}

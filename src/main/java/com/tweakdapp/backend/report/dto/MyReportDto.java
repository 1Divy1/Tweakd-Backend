package com.tweakdapp.backend.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One report the current user has filed, for the "my submitted reports" section. Reports of all
 * three kinds (post / comment / profile) are returned in a single feed, discriminated by
 * {@code targetType}.
 *
 * <p>Deliberately self-contained: it carries only what the report tables know, so listing a
 * reporter's own reports needs no cross-module lookup (and no dependency on {@code posts} /
 * {@code profile}). If the UI needs the reported thing itself, it fetches it via {@code targetId}.
 *
 * @param targetType {@code "post"}, {@code "comment"}, or {@code "profile"}
 * @param targetId   the reported post / comment / profile UUID
 * @param reason     the human-readable preset reason text, or {@code null} if none was picked
 * @param status     moderation status: {@code "pending"}, {@code "in_progress"}, {@code "resolved"},
 *                   or {@code "dismissed"}
 * @param createdAt  when the report was filed
 */
public record MyReportDto(
        String targetType,
        UUID targetId,
        String reason,
        String status,
        Instant createdAt) {
}

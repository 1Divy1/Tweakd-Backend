package com.carsocialmedia.backend.report.internal.enums;

/**
 * Moderation lifecycle of a report.
 *
 * <p>Backed by the native Postgres enum {@code report_status}. The constant names
 * intentionally match the lowercase enum labels exactly, since Hibernate binds the
 * {@link Enum#name()} when writing to a {@code NAMED_ENUM} column and Postgres matches
 * labels case-sensitively. New reports default to {@link #pending} via the column default.
 */
public enum ReportStatus {
    pending,
    in_progress,
    resolved,
    dismissed
}

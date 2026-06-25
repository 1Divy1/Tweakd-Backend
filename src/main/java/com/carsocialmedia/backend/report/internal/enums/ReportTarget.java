package com.carsocialmedia.backend.report.internal.enums;

/**
 * The kind of entity a {@code report_reasons} row applies to.
 *
 * <p>Backed by the native Postgres enum {@code target_entity}. The constant names
 * intentionally match the lowercase enum labels exactly, since Hibernate binds the
 * {@link Enum#name()} when writing to a {@code NAMED_ENUM} column and Postgres matches
 * labels case-sensitively.
 */
public enum ReportTarget {
    post,
    comment,
    profile
}

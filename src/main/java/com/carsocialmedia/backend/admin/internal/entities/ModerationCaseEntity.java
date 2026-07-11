package com.carsocialmedia.backend.admin.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One moderation case: all reports against one target, folded into a single queue row keyed by
 * {@code (target_type, target_id)}. Rows are <em>only ever created by the DB trigger</em> that
 * fires on report inserts (which also bumps {@code last_reported_at} and reopens a resolved case on
 * a fresh report) — this module reads and resolves cases, it never inserts them. The bigint id is
 * the dashboard's human-friendly "Case #4821".
 */
@Entity
@Table(name = "moderation_cases")
@Getter
@Setter
public class ModerationCaseEntity {

    @Id
    private Long id;

    /** {@code post} / {@code comment} / {@code profile} / {@code forum_thread} / {@code forum_thread_reply}. */
    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** {@code open} / {@code escalated} / {@code resolved}. */
    @Column(name = "status", nullable = false)
    private String status;

    /** How it was closed: {@code approved} / {@code content_removed} / {@code author_warned} / {@code author_banned}. */
    @Column(name = "resolution")
    private String resolution;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** DB-managed: bumped by the report trigger — the queue's freshness sort key. */
    @Column(name = "last_reported_at", insertable = false, updatable = false)
    private Instant lastReportedAt;
}

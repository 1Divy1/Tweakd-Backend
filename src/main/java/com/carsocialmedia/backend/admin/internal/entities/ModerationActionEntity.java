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
 * The immutable audit log of moderator decisions. One row per action (approve / remove_content /
 * warn / ban / unban / escalate). For a content removal, {@code contentSnapshot} preserves the text
 * <em>before</em> the hard delete — the report rows CASCADE away with the content, so this row is
 * the only surviving record of what was removed. FKs are soft ({@code ON DELETE SET NULL}) so the
 * audit trail outlives cases, moderators, and authors.
 */
@Entity
@Table(name = "moderation_actions")
@Getter
@Setter
public class ModerationActionEntity {

    @Id
    private UUID id;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "moderator_id")
    private UUID moderatorId;

    /** {@code approve} / {@code remove_content} / {@code warn} / {@code ban} / {@code unban} / {@code escalate}. */
    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_author_id")
    private UUID targetAuthorId;

    /** The removed content's text, captured before the hard delete. */
    @Column(name = "content_snapshot")
    private String contentSnapshot;

    /** Free-form moderator note (warn message, ban reason, escalation context). */
    @Column(name = "note")
    private String note;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

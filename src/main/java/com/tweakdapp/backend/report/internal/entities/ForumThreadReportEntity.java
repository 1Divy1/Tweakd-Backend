package com.tweakdapp.backend.report.internal.entities;

import com.tweakdapp.backend.report.internal.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A report filed against a forum thread by a user. Mirrors {@link PostReportEntity}: the reporter
 * and target are flat UUID references (owned by other modules); {@code status} / {@code created_at}
 * are DB-managed.
 */
@Entity
@Table(name = "forum_thread_reports")
@Getter
@Setter
public class ForumThreadReportEntity {

    @EmbeddedId
    private ForumThreadReportId id;

    /** Chosen {@code report_reasons} row; nullable (a reporter may not pick a preset reason). */
    @Column(name = "reason_id")
    private UUID reasonId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", insertable = false)
    private ReportStatus status;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

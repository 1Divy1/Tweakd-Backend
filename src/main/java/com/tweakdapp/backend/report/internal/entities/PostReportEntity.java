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
 * A report filed against a post by a user. {@code reporterId} is a flat reference to the
 * Supabase profile UUID, since {@code profiles} is owned by another module.
 */
@Entity
@Table(name = "post_reports")
@Getter
@Setter
public class PostReportEntity {

    @EmbeddedId
    private PostReportId id;

    /** Chosen {@code report_reasons} row; nullable (a reporter may not pick a preset reason). */
    @Column(name = "reason_id")
    private UUID reasonId;

    /**
     * Moderation status. Native Postgres enum {@code report_status}. Left out of inserts
     * ({@code insertable = false}) so the column DEFAULT {@code 'pending'} fires; moderators
     * update it afterwards.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", insertable = false)
    private ReportStatus status;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

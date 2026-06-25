package com.carsocialmedia.backend.report.internal.entities;

import com.carsocialmedia.backend.report.internal.enums.ReportStatus;
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
 * A report filed against a profile by a user. Both {@code profileId} (the reported user) and
 * {@code reporterId} are flat references to Supabase profile UUIDs, since {@code profiles} is
 * owned by another module.
 */
@Entity
@Table(name = "profile_reports")
@Getter
@Setter
public class ProfileReportEntity {

    @EmbeddedId
    private ProfileReportId id;

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

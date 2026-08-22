package com.tweakdapp.backend.report.internal.entities;

import com.tweakdapp.backend.report.internal.enums.ReportTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A predefined report reason, scoped to a single {@link ReportTarget} (post / comment /
 * profile). Rows are reference data seeded in Supabase; the app reads them to populate the
 * report UI and links them from the per-target report tables via {@code reason_id}.
 */
@Entity
@Table(name = "report_reasons")
@Getter
@Setter
public class ReportReasonEntity {

    @Id
    private UUID id;

    /** Which entity type this reason applies to. Native Postgres enum {@code target_entity}. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "target", nullable = false)
    private ReportTarget target;

    @Column(name = "reason", nullable = false)
    private String reason;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

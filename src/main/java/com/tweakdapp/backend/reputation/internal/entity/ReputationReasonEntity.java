package com.tweakdapp.backend.reputation.internal.entity;

import com.tweakdapp.backend.reputation.dto.ReputationReasonDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One way to gain or lose reputation.
 *
 * <p>Reference data table ({@code reputation_score_reason_options}), seeded in Supabase and never
 * written from here. {@code reputation_score_history.reason} is a foreign key to {@link #id}, so a
 * reason is retired by clearing {@link #active} — deleting one would be refused by the FK, and
 * rightly so: the history rows earned under it must stay readable.
 *
 * <p>The label column is named {@code reason}, matching the table's own convention (as
 * {@code app_language_options.language} and {@code report_reasons.reason} do); it is mapped to
 * {@code label} here because on a history entry "the reason" is the code, not the prose. It is the
 * only prose the catalogue carries — {@code reason} already reads as a full sentence, so there is
 * deliberately no separate description column.
 */
@Entity
@Table(name = "reputation_score_reason_options")
@Getter
@Setter
public class ReputationReasonEntity {

    /** The stable code recorded on every history row awarded under this reason. */
    @Id
    private String id;

    @Column(name = "reason", nullable = false)
    private String label;

    /** Default delta. Negative for a {@code moderation} penalty; the DB refuses zero. */
    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "category", nullable = false)
    private String category;

    /**
     * {@code false} = one-time achievement: a second award returns the entry already on file
     * instead of paying again. Every seeded reason is currently repeatable; per-occurrence
     * double-awarding is prevented by the source link, not by this flag.
     */
    @Column(name = "is_repeatable", nullable = false)
    private boolean repeatable;

    /** Retired reasons stay readable on old history rows but can no longer be awarded. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public ReputationReasonDto toDto() {
        return new ReputationReasonDto(id, label, points, category, repeatable);
    }
}

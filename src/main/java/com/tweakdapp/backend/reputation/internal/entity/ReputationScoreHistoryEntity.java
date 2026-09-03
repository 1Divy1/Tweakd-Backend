package com.tweakdapp.backend.reputation.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One achievement in a user's reputation timeline.
 *
 * <p>The row is an immutable record of a moment: {@code previousScore} and {@code newScore} are the
 * values the score actually moved between, captured under the profile row lock in the same
 * transaction. They are stored rather than derived so the timeline still reads correctly after the
 * catalogue's point values change, and so a penalty clamped at zero shows the truth.
 *
 * <p>{@code userId} is a flat reference to {@code profiles.id} — the {@code profile} module owns
 * that table, so there is no association across the boundary. The reason, by contrast, is owned
 * here and mapped as a {@code @ManyToOne} so the history read can carry its label in one query.
 *
 * <p>The {@code source*} trio records <em>which</em> event, thread or car earned the entry. It is
 * an unenforced link on purpose — no foreign key — and {@code sourceLabel} is a snapshot taken at
 * award time, so an entry outlives the row behind it and this module never reads another module's
 * tables to render a timeline. The database's partial unique index on
 * {@code (user_id, reason, source_type, source_id)} is what makes a sourced award idempotent.
 *
 * <p>A revoked entry ({@link #revokedAt} set) is kept but no longer counts: it is subtracted from
 * the score and hidden from the public timeline, visible only to the owner. The unique index is
 * partial on {@code revoked_at is null}, so revoking frees the same source to be earned again.
 */
@Entity
@Table(name = "reputation_score_history")
@Getter
@Setter
public class ReputationScoreHistoryEntity {

    /**
     * Set client-side by the service, matching the rest of the codebase. The column also has a
     * {@code gen_random_uuid()} default, which only applies to rows inserted outside the app.
     */
    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reason", nullable = false)
    private ReputationReasonEntity reason;

    /** The delta actually applied, already clamped if a penalty would have gone below zero. */
    @Column(name = "score_gain", nullable = false)
    private int scoreGain;

    @Column(name = "previous_score", nullable = false)
    private int previousScore;

    @Column(name = "new_score", nullable = false)
    private int newScore;

    /** One of the {@code ReputationSourceType} constants, or {@code null} for a sourceless entry. */
    @Column(name = "source_type")
    private String sourceType;

    /** The source row's id. Not a foreign key — see the class javadoc. */
    @Column(name = "source_id")
    private UUID sourceId;

    /** The source's display name as it stood when the points were awarded. */
    @Column(name = "source_label")
    private String sourceLabel;

    /**
     * When this award was taken back, or {@code null} while it stands.
     *
     * <p>The one mutable field on an otherwise immutable row. Set rather than deleting the row so
     * the owner can still see what happened to their score, and so the audit trail survives.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Why it was taken back. Shown to the owner only, never on a public timeline. */
    @Column(name = "revoked_reason")
    private String revokedReason;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

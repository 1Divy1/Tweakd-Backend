package com.tweakdapp.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A forum thread — the single pool of threads, reachable through the brand/model and topic lenses.
 *
 * <p>Several columns are maintained by Supabase triggers and are mapped read-only here — the app
 * must never write them:
 * <ul>
 *   <li>{@code ranking_score} — Reddit-style hot score, recomputed on every insert/update.</li>
 *   <li>{@code likes_count}, {@code reply_count}, {@code last_activity_at} — maintained by triggers
 *       on {@code forum_posts} / {@code forum_thread_likes}.</li>
 * </ul>
 *
 * <p>{@code brand_id} is special: a {@code BEFORE INSERT/UPDATE} trigger auto-derives it from
 * {@code model_id}. The app sets {@code model_id} for a model-scoped thread (leaving {@code brand_id}
 * null, which the trigger overwrites), or sets {@code brand_id} directly for a brand-level thread
 * with no model. It is therefore {@code insertable} (to allow the brand-level case) but never
 * {@code updatable} — the app never rewrites it after creation.
 */
@Entity
@Table(name = "forum_threads")
@Getter
@Setter
public class ForumThreadEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    /** Optional OP body. */
    @Column(name = "content")
    private String content;

    /**
     * Trigger-derived from {@code model_id} when a model is present; set directly by the app only for
     * a brand-level thread (no model). Never updated from the app.
     */
    @Column(name = "brand_id", updatable = false)
    private UUID brandId;

    @Column(name = "model_id", updatable = false)
    private UUID modelId;

    @Column(name = "likes_count", nullable = false, insertable = false, updatable = false)
    private int likesCount;

    @Column(name = "reply_count", nullable = false, insertable = false, updatable = false)
    private int replyCount;

    /** Reddit-style hot score, maintained by a Supabase trigger. Drives the "hot" sort. */
    @Column(name = "ranking_score", nullable = false, insertable = false, updatable = false)
    private double rankingScore;

    /**
     * Author-delete flag = <em>anonymized</em>: the thread stays fully visible, readable, and
     * repliable with title/content/replies intact, but the author is hidden in every DTO. A thread
     * with zero replies is hard-deleted instead of flagged.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /** Moderation flag (mod-managed). Read-only from the app in this module. */
    @Column(name = "is_locked", nullable = false, updatable = false)
    private boolean locked;

    /** Moderation flag (mod-managed). Read-only from the app in this module. */
    @Column(name = "is_pinned", nullable = false, updatable = false)
    private boolean pinned;

    /** DB-managed: DEFAULT now() in Supabase. Feeds the ranking-score time term. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bumped by triggers on every reply / like. Drives the "active" sort. Read-only. */
    @Column(name = "last_activity_at", nullable = false, insertable = false, updatable = false)
    private Instant lastActivityAt;
}

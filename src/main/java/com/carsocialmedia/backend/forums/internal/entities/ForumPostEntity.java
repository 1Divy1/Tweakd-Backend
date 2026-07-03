package com.carsocialmedia.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A reply within a thread (Reddit-style threading). {@code parentPostId} is {@code null} for a
 * top-level reply, or the id of the parent reply for a nested one (self-referential FK).
 *
 * <p>{@code likes_count} and {@code reply_count} (direct children) are maintained by Supabase
 * triggers and are read-only here.
 */
@Entity
@Table(name = "forum_posts")
@Getter
@Setter
public class ForumPostEntity {

    @Id
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Parent reply for nested threading; {@code null} for a top-level reply. */
    @Column(name = "parent_post_id")
    private UUID parentPostId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "likes_count", nullable = false, insertable = false, updatable = false)
    private int likesCount;

    /** Number of direct children. Trigger-maintained; read-only. */
    @Column(name = "reply_count", nullable = false, insertable = false, updatable = false)
    private int replyCount;

    /**
     * Soft-delete flag. A deleted reply with children keeps its row (rendered as "[deleted]" with
     * its children still visible); a childless reply is hard-deleted instead.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.carsocialmedia.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment on a post. {@code userId} is the Supabase profile UUID of the author —
 * a flat reference, since {@code profiles} is owned by another module.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
public class CommentEntity {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content", nullable = false)
    private String content;

    /**
     * Parent comment for threaded replies (Reddit-style). {@code null} for a root comment.
     * Self-referential FK to {@code comments.id}.
     */
    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    /**
     * Soft-delete flag. When {@code true} the row is kept (it may still anchor replies)
     * but the client renders it as "[deleted]" — the content is not surfaced.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "likes_count", nullable = false)
    private int likesCount;

    @Column(name = "reply_count", nullable = false)
    private int replyCount;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

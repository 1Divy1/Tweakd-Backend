package com.tweakdapp.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Marks that a user has opened (seen) a thread. Its presence excludes the thread from that user's
 * shortcut "unread" counts. The app only inserts this row (idempotently); {@code created_at} is
 * DB-managed.
 */
@Entity
@Table(name = "forum_thread_reads")
@Getter
@Setter
public class ForumThreadReadEntity {

    @EmbeddedId
    private ForumThreadReadId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

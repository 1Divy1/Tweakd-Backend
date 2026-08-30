package com.tweakdapp.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's save (bookmark) of a thread. The app only inserts / deletes this row; {@code created_at}
 * is DB-managed and drives the newest-first ordering of the user's "saved threads" list.
 */
@Entity
@Table(name = "forum_thread_saves")
@Getter
@Setter
public class ForumThreadSaveEntity {

    @EmbeddedId
    private ForumThreadSaveId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

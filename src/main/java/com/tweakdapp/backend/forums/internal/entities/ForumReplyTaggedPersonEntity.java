package com.tweakdapp.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a thread reply to a profile tagged in it. The {@code userId} is a flat reference to a
 * {@code profiles} row owned by the profile module.
 */
@Entity
@Table(name = "forum_thread_reply_tagged_people")
@Getter
@Setter
public class ForumReplyTaggedPersonEntity {

    @EmbeddedId
    private ForumReplyTaggedPersonId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

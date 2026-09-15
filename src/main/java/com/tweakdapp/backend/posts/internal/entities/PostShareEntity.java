package com.tweakdapp.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A repost: a user putting someone else's post in front of their followers. Nothing of the
 * reposter's own is attached — the row is the whole fact.
 */
@Entity
@Table(name = "post_shares")
@Getter
@Setter
public class PostShareEntity {

    @EmbeddedId
    private PostShareId id;

    /** DB-managed: DEFAULT now() in Supabase. What the feed ranks a repost by. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

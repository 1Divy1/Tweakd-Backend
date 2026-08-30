package com.tweakdapp.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A share of a post by a user, optionally with the sharer's own thoughts ({@code content}).
 */
@Entity
@Table(name = "post_shares")
@Getter
@Setter
public class PostShareEntity {

    @EmbeddedId
    private PostShareId id;

    /** Optional text the sharer adds when re-sharing the post. */
    @Column(name = "content")
    private String content;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

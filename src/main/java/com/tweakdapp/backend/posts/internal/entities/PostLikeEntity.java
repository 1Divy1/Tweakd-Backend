package com.tweakdapp.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A like on a post by a user. The {@code likes_count} on the post is maintained by a
 * Supabase trigger, so the app does not adjust it from here.
 */
@Entity
@Table(name = "post_likes")
@Getter
@Setter
public class PostLikeEntity {

    @EmbeddedId
    private PostLikeId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

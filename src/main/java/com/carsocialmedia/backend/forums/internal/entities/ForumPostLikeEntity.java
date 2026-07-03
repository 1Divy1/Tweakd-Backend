package com.carsocialmedia.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A like on a reply by a user. The reply's {@code likes_count} is maintained by a Supabase trigger,
 * so the app only inserts / deletes this row and never touches the count.
 */
@Entity
@Table(name = "forum_post_likes")
@Getter
@Setter
public class ForumPostLikeEntity {

    @EmbeddedId
    private ForumPostLikeId id;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

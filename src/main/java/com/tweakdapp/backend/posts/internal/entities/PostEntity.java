package com.tweakdapp.backend.posts.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
public class PostEntity {

    @Id
    private UUID id;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "likes_count_enabled", nullable = false)
    private boolean likesCountEnabled;

    @Column(name = "comments_count_enabled", nullable = false)
    private boolean commentsCountEnabled;

    @Column(name = "shares_count_enabled", nullable = false)
    private boolean sharesCountEnabled;

    @Column(name = "saved_count_enabled", nullable = false)
    private boolean savedCountEnabled;

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    @Column(name = "comments_count", nullable = false)
    private Long commentsCount;

    @Column(name = "shares_count", nullable = false)
    private Long sharesCount;

    /**
     * Number of "quote" shares — a re-share with the user's own description attached (Facebook-style).
     * Maintained by the share action / Supabase counters; the total share count shown in a post is
     * {@code sharesCount + quoteSharesCount}.
     */
    @Column(name = "quote_shares_count", nullable = false)
    private Long quoteSharesCount;

    @Column(name = "saved_count", nullable = false)
    private Long savedCount;

    /**
     * Virality score maintained by a Supabase trigger ({@code compute_post_ranking_score}) from
     * the post's engagement counts and age. App code never writes this — it is read-only here and
     * drives the global feed ordering.
     */
    @Column(name = "ranking_score", nullable = false, insertable = false, updatable = false)
    private double rankingScore;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

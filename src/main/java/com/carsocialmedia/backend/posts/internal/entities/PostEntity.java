package com.carsocialmedia.backend.posts.internal.entities;

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

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    @Column(name = "comments_count", nullable = false)
    private Long commentsCount;

    @Column(name = "shares_count", nullable = false)
    private Long sharesCount;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

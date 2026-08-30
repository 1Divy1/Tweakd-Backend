package com.tweakdapp.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's saved forum filter — any combination of brand / model / topic, pinned to their landing
 * screen, drag-reorderable ({@code sortOrder}) with an optional {@code notify} flag. Owner-scoped:
 * every read/write is constrained to {@code userId = current user}.
 *
 * <p>The DB enforces a CHECK that at least one of {@code brandId} / {@code modelId} / {@code topicId}
 * is non-null; the service validates the same before insert.
 */
@Entity
@Table(name = "forum_shortcuts")
@Getter
@Setter
public class ForumShortcutEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "model_id")
    private UUID modelId;

    /** Topic slug (text), or {@code null}. */
    @Column(name = "topic_id")
    private String topicId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "notify", nullable = false)
    private boolean notify;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

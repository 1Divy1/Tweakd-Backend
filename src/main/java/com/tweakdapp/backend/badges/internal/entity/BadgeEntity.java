package com.tweakdapp.backend.badges.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One unlockable badge.
 *
 * <p>Reference data ({@code badges}), curated from the admin dashboard. {@code user_badges.badge_id}
 * is a foreign key to {@link #id} with {@code ON DELETE RESTRICT}, so a badge someone has earned
 * cannot be deleted — it is withdrawn by clearing {@link #available} instead, which stops new
 * awards while leaving the profiles that hold it alone.
 *
 * <p>The two url columns hold <strong>R2 object keys</strong>, not URLs
 * ({@code badges/pioneer/badge-unlocked.svg}). The full public URL is built at read time from the
 * configured {@code app-assets} bucket, so moving the bucket or its domain is a config change
 * rather than a data migration. A CHECK constraint refuses a value starting with {@code http},
 * because the failure mode otherwise is a silently doubled prefix and a broken image in the app.
 */
@Entity
@Table(name = "badges")
@Getter
@Setter
public class BadgeEntity {

    /** The stable code recorded on every unlock. Chosen once; renaming it cascades to holders. */
    @Id
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    /** What it takes to unlock the badge. Optional. */
    @Column(name = "description")
    private String description;

    /** R2 key of the earned artwork. */
    @Column(name = "unlocked_badge_url", nullable = false)
    private String unlockedKey;

    /** R2 key of the not-yet-earned artwork, or {@code null} if the badge has no locked variant. */
    @Column(name = "locked_badge_url")
    private String lockedKey;

    /** Retired badges stay on the profiles that earned them but can no longer be awarded. */
    @Column(name = "is_available", nullable = false)
    private boolean available;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

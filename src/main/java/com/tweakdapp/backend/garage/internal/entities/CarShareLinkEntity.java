package com.tweakdapp.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * A car's public share link: the code behind {@code https://web.tweakdapp.com/c/{code}} and behind the
 * QR sticker that encodes the same URL.
 *
 * <p>One live row per car (the partial unique index {@code car_share_links_active_car_uq}), and a
 * code that is never reissued once it exists. Both facts follow from the same thing: the code can
 * be printed and stuck on a car, so it has to keep meaning what it meant when it was printed.
 *
 * <p>The three counter columns are deliberately {@code insertable = false, updatable = false}: they
 * are only ever moved by {@link com.tweakdapp.backend.garage.internal.repositories.CarShareLinkRepository#recordView}'s
 * bulk update, which increments in the database. Letting the ORM write them would turn every
 * concurrent view into a read-modify-write that loses counts.
 */
@Entity
@Table(name = "car_share_links")
@DynamicUpdate
@Getter
@Setter
public class CarShareLinkEntity {

    /** The share link ID (UUID, PK). */
    @Id
    private UUID id;

    /** The shared car (required, FK to cars.id, cascades on delete). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "car_id")
    private CarEntity car;

    /**
     * The profile the code was issued to. A raw UUID rather than an entity reference: profiles
     * belong to another module, exactly as {@code dream_cars.profile_id} does.
     */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** The public code, canonical uppercase Crockford base32. Immutable once written. */
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    /** Owner-controlled pause. false = the public read answers 410, but the code stays reserved. */
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    /** When the link was minted (managed by Supabase, read-only). */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Set only by system events (car transfer). A revoked code answers 410 forever. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Non-QR views. DB-incremented; see the class comment. */
    @Column(name = "view_count", insertable = false, updatable = false)
    private long viewCount;

    /** Views that carried {@code ?s=qr}. DB-incremented; see the class comment. */
    @Column(name = "qr_scan_count", insertable = false, updatable = false)
    private long qrScanCount;

    /** Last counted view or scan. DB-written; see the class comment. */
    @Column(name = "last_viewed_at", insertable = false, updatable = false)
    private Instant lastViewedAt;
}

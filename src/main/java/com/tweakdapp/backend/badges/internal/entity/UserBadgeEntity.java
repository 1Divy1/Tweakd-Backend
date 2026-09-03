package com.tweakdapp.backend.badges.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One badge unlocked by one user.
 *
 * <p>A unique index on {@code (user_id, badge_id)} is what makes awarding idempotent: the second
 * insert of the same pair fails at flush rather than creating a duplicate, so callers can fire
 * {@code award} from a retried listener without a guard of their own.
 *
 * <p>There is no soft-delete. Revoking removes the row, which frees the badge to be earned again —
 * a badge says what is true now, unlike a reputation entry, which is a ledger line that has to
 * survive being taken back.
 *
 * <p>The badge association is {@code LAZY} but always fetched by the repository's {@code join
 * fetch} queries; every read of a held badge renders its title and artwork, so resolving it
 * per-row would be N+1 across a profile's badge list.
 */
@Entity
@Table(name = "user_badges")
@Getter
@Setter
public class UserBadgeEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private BadgeEntity badge;

    /**
     * The staff member who granted this by hand, or {@code null} when the backend awarded it
     * automatically. Not a foreign key — staff live in {@code admin_team_members}, app users in
     * {@code profiles}, and removing a staff member must not disturb the badges they granted.
     */
    @Column(name = "granted_by")
    private UUID grantedBy;

    /** When the badge was unlocked. DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

package com.carsocialmedia.backend.admin.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One dashboard team member — the staff identity itself. Staff accounts are separate from app
 * accounts: the primary key is a Supabase auth UUID that has <em>no</em> {@code profiles} row
 * (the {@code handle_new_user} trigger skips staff), so {@code email} / {@code displayName} /
 * {@code avatarUrl} live here rather than being joined from a profile. {@code role} is the
 * fine-grained dashboard role (owner / senior_admin / content_moderator / support_agent /
 * technical); what each role may do is decided in code by {@code AdminAccessService}, not in
 * the DB.
 *
 * <p>Note the second auth layer: this row alone doesn't open the dashboard — the staff user's
 * Supabase {@code app_metadata.role} must also be {@code admin} to pass the
 * {@code /api/v1/admin/**} gate (set by {@code SupabaseAuthAdminClient} at invite time).
 */
@Entity
@Table(name = "admin_team_members")
@Getter
@Setter
public class AdminTeamMemberEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role", nullable = false)
    private String role;

    /** {@code invited} (default) until the member's first admin API call flips it {@code active}. */
    @Column(name = "status")
    private String status;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "invited_by")
    private UUID invitedBy;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Touched (throttled) on every admin API call, for the Moderators page's "last active". */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;
}

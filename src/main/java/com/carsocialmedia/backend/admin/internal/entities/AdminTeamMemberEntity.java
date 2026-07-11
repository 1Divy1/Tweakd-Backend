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
 * One dashboard team member. The primary key <em>is</em> the profile UUID — a user is on the team
 * at most once. {@code role} is the fine-grained dashboard role (owner / senior_admin /
 * content_moderator / support_agent / technical); what each role may do is decided in code by
 * {@code AdminAccessService}, not in the DB.
 *
 * <p>Note the second auth layer: this row alone doesn't open the dashboard — the user's Supabase
 * {@code app_metadata.role} must also be {@code admin} to pass the {@code /api/v1/admin/**} gate.
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

    /** {@code invited} (default) or {@code active}; members added by username start {@code active}. */
    @Column(name = "status")
    private String status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Touched (throttled) on every admin API call, for the Moderators page's "last active". */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;
}

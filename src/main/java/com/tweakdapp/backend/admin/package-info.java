/**
 * The backend of the "Tweakd." admin dashboard: team management, the overview analytics, the
 * content-moderation queue, and the admin sides of feedback and support.
 *
 * <p>This module is a pure orchestrator sitting on top of the content modules — it asks
 * {@code posts} / {@code forums} / {@code profile} for moderation snapshots and moderator deletes,
 * {@code report} for the reports behind a case, {@code feedback} / {@code support} for their admin
 * operations, and {@code notification} to notify affected users. Nothing depends on it, so its
 * whole surface (controllers, services, DTOs) lives in {@code internal}.
 *
 * <p>Auth is two layers: the security chain requires {@code ROLE_ADMIN} (Supabase
 * {@code app_metadata.role = 'admin'}) for {@code /api/v1/admin/**}, and every endpoint then runs a
 * fine-grained capability check against the caller's {@code admin_team_members} role.
 */
@ApplicationModule(
        displayName = "Admin"
)
package com.tweakdapp.backend.admin;

import org.springframework.modulith.ApplicationModule;

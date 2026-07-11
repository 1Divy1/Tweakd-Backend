package com.carsocialmedia.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Adds an existing app user to the dashboard team by username. (Email invites through the Supabase
 * Auth admin API are a deferred TODO — see the progress file.)
 *
 * @param role {@code senior_admin} / {@code content_moderator} / {@code support_agent} /
 *        {@code technical} — never {@code owner}
 */
public record AddTeamMemberRequest(
        @NotBlank String username,
        @NotBlank String role) {
}

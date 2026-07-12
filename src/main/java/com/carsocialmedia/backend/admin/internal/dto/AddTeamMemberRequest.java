package com.carsocialmedia.backend.admin.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Invites a new staff member by email (Supabase sends the invite mail; the address must not belong
 * to any existing auth user — staff accounts are separate from app accounts).
 *
 * @param role {@code senior_admin} / {@code content_moderator} / {@code support_agent} /
 *        {@code technical} — never {@code owner}
 */
public record AddTeamMemberRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName,
        @NotBlank String role) {
}

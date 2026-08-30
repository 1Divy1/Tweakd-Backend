package com.tweakdapp.backend.admin.internal.dto;

import jakarta.validation.constraints.NotBlank;

/** Re-roles a team member (never to / from {@code owner}). */
public record UpdateTeamMemberRequest(
        @NotBlank String role) {
}

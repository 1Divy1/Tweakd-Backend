package com.carsocialmedia.backend.admin.exception;

import com.carsocialmedia.backend.shared.exception.ForbiddenException;

/**
 * Thrown when a caller passes the {@code ROLE_ADMIN} security gate but has no
 * {@code admin_team_members} row — the JWT says admin, the team table doesn't. Maps to HTTP 403.
 */
public class NotTeamMemberException extends ForbiddenException {

    public NotTeamMemberException() {
        super("You are not a member of the admin team");
    }
}

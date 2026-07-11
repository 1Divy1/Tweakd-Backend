package com.carsocialmedia.backend.admin.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/** Thrown when adding a user who already has a team row. Maps to HTTP 409. */
public class AlreadyTeamMemberException extends ConflictException {

    public AlreadyTeamMemberException(String username) {
        super("User is already a team member: " + username);
    }
}

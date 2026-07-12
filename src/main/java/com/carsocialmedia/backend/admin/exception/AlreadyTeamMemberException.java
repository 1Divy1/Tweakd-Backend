package com.carsocialmedia.backend.admin.exception;

import com.carsocialmedia.backend.shared.exception.ConflictException;

/** Thrown when inviting an email that already has a team row. Maps to HTTP 409. */
public class AlreadyTeamMemberException extends ConflictException {

    public AlreadyTeamMemberException(String email) {
        super("Already a team member: " + email);
    }
}

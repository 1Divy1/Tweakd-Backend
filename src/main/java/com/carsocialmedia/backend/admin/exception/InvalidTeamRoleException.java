package com.carsocialmedia.backend.admin.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/**
 * Thrown when a team request carries an unknown role, or tries to hand out {@code owner} (the
 * owner role only moves via ownership transfer, never via add / re-role). Maps to HTTP 400.
 */
public class InvalidTeamRoleException extends BadRequestException {

    public InvalidTeamRoleException(String message) {
        super(message);
    }
}

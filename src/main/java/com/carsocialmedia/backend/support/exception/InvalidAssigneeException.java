package com.carsocialmedia.backend.support.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

import java.util.UUID;

/** Thrown when a ticket is assigned to someone who is not a staff team member. Maps to HTTP 400. */
public class InvalidAssigneeException extends BadRequestException {

    public InvalidAssigneeException(UUID assigneeId) {
        super("Not a staff team member: " + assigneeId);
    }
}

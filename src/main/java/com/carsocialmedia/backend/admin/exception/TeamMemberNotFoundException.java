package com.carsocialmedia.backend.admin.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** Thrown when a team operation addresses a user who is not on the team. Maps to HTTP 404. */
public class TeamMemberNotFoundException extends NotFoundException {

    public TeamMemberNotFoundException(UUID userId) {
        super("Team member not found: " + userId);
    }
}

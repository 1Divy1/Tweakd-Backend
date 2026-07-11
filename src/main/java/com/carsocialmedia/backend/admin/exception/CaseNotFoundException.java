package com.carsocialmedia.backend.admin.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

/** Thrown when a moderation case addressed by id does not exist. Maps to HTTP 404. */
public class CaseNotFoundException extends NotFoundException {

    public CaseNotFoundException(long caseId) {
        super("Moderation case not found: #" + caseId);
    }
}

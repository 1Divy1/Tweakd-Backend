package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

/** Thrown when a moderation case addressed by id does not exist. Maps to HTTP 404. */
public class CaseNotFoundException extends NotFoundException {

    public CaseNotFoundException(long caseId) {
        super("Moderation case not found: #" + caseId);
    }
}

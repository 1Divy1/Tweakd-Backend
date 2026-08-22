package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.ForbiddenException;

/** Thrown when a team operation targets the owner's row (re-role, removal). Maps to HTTP 403. */
public class CannotModifyOwnerException extends ForbiddenException {

    public CannotModifyOwnerException() {
        super("The owner's team membership cannot be modified");
    }
}

package com.tweakdapp.backend.admin.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/**
 * Thrown when a staff invite targets an email that already has a Supabase auth user — an app
 * account or another staff member. Staff identities are separate from app accounts, so a staff
 * member must join with an email their app account does not use. Maps to HTTP 409.
 */
public class StaffEmailInUseException extends ConflictException {

    public StaffEmailInUseException(String email) {
        super("An account already exists for " + email
                + " — staff accounts need an email that is not used by an app account");
    }
}

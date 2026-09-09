package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/** Raised when something needs an open contest — a vote, an early finish — and voting has not started. */
public class ContestNotOpenException extends ConflictException {
    public ContestNotOpenException(String message) {
        super(message);
    }
}

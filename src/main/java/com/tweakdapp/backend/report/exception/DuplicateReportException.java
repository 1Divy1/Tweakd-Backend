package com.tweakdapp.backend.report.exception;

import com.tweakdapp.backend.shared.exception.ConflictException;

/**
 * Thrown when a reporter files a second report against a target they have already reported (each
 * report table has a composite {@code (target, reporter)} primary key). Maps to HTTP 409 via the
 * shared {@code GlobalExceptionHandler}.
 */
public class DuplicateReportException extends ConflictException {

    public DuplicateReportException() {
        super("You have already reported this");
    }
}

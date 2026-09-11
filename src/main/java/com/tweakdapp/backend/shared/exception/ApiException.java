package com.tweakdapp.backend.shared.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** A stable code the app can branch on, sent as {@code error}; {@code null} unless overridden. */
    public String getErrorCode() {
        return null;
    }

    /**
     * Structured detail the message cannot carry (e.g. when a cooldown ends), sent as
     * {@code details}; {@code null} unless overridden.
     */
    public Map<String, Object> getDetails() {
        return null;
    }
}

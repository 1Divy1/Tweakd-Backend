package com.tweakdapp.backend.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class ConflictException extends ApiException {

    protected ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}

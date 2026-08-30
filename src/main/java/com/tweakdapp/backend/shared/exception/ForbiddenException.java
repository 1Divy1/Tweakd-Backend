package com.tweakdapp.backend.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class ForbiddenException extends ApiException {

    protected ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}

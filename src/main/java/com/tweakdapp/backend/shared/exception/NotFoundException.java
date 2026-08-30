package com.tweakdapp.backend.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class NotFoundException extends ApiException {

    protected NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}

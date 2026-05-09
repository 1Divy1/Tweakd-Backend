package com.carsocialmedia.backend.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class BadRequestException extends ApiException {

    protected BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

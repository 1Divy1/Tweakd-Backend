package com.carsocialmedia.backend.storage.internal.exceptions;

import com.carsocialmedia.backend.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StorageServiceException extends ApiException {
    public StorageServiceException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}

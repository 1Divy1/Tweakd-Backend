package com.tweakdapp.backend.forums.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Thrown when a shortcut write is semantically invalid: no brand/model/topic set at all, a blank
 * name on update, or a reorder list that is not exactly the caller's shortcut set. Maps to HTTP 400
 * via the shared {@code GlobalExceptionHandler}.
 */
public class InvalidShortcutException extends BadRequestException {

    public InvalidShortcutException(String message) {
        super(message);
    }
}

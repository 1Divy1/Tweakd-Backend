package com.tweakdapp.backend.forums.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when a shortcut does not exist or is not owned by the current user. Maps to HTTP 404 via
 * the shared {@code GlobalExceptionHandler}.
 */
public class ShortcutNotFoundException extends NotFoundException {

    public ShortcutNotFoundException(UUID shortcutId) {
        super("Shortcut not found: " + shortcutId);
    }
}

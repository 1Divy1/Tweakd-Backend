package com.tweakdapp.backend.dms.dto;

import java.util.UUID;

/**
 * A typing-indicator ping, sent by the client over the WebSocket to {@code /app/dms/typing}.
 * {@code typing == true} on keystroke (the client should throttle), {@code false} when the input
 * empties or the user leaves the screen. Never persisted.
 */
public record TypingRequest(
        UUID conversationId,
        boolean typing
) {}

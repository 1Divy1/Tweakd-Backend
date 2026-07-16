package com.carsocialmedia.backend.dms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Sends one DM. Addressed by recipient (not conversation) so the very first message of a pair
 * creates the conversation implicitly — the client never has to "create a chat" first.
 */
public record SendMessageRequest(
        @NotNull UUID recipientId,
        @NotBlank @Size(max = 2000) String content
) {}

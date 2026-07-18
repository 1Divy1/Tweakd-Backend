package com.carsocialmedia.backend.dms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Sends one DM. Addressed by recipient (not conversation) so the very first message of a pair
 * creates the conversation implicitly — the client never has to "create a chat" first.
 *
 * <p>A message is valid when it carries non-blank {@code content} <em>or</em> at least one
 * {@code taggedCarId} (a car-only message is allowed — the recipient's client renders a
 * "shared cars" card). {@code content} is therefore optional, but still capped at 2000 chars.
 * {@code taggedCarIds} may reference any user's cars (owners are neither tagged nor notified);
 * they are de-duplicated silently and capped at 10 by the service.
 *
 * @param recipientId the profile to send to (required)
 * @param content the message text (optional; blank/absent allowed when cars are tagged)
 * @param taggedCarIds ids of cars to tag in the message (optional)
 */
public record SendMessageRequest(
        @NotNull UUID recipientId,
        @Size(max = 2000) String content,
        List<UUID> taggedCarIds
) {}

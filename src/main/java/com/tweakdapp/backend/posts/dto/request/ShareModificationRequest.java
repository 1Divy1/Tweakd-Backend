package com.tweakdapp.backend.posts.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload for sharing a build-log modification to the feed. It only <em>names</em> the mod: the
 * server decides whether it exists and is the caller's, and derives everything drawn on the card,
 * so nothing here can claim a mod or a price.
 *
 * <p>There is no caption. The share is a toggle inside the "log a mod" flow, and the mod's own
 * description is the text — asking for a second write-up at that moment would only add friction.
 *
 * @param modificationId the modification to share
 */
public record ShareModificationRequest(
        @NotNull UUID modificationId
) {}

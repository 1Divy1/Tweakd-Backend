package com.tweakdapp.backend.posts.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for sharing a participant card to the feed. It only <em>names</em> the card: the server
 * decides whether it exists and is the caller's, and derives everything drawn on it, so nothing
 * here can claim a placement.
 *
 * @param eventId     the card's event
 * @param carId       the card's car
 * @param description optional caption (null is treated as empty)
 */
public record ShareParticipantCardRequest(
        @NotNull UUID eventId,
        @NotNull UUID carId,
        @Size(max = 2200) String description
) {}

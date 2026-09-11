package com.tweakdapp.backend.mapevents.dto;

import java.util.UUID;

/**
 * Identifies a participant card. A card is not stored — it <em>is</em> this pair, derived on read
 * (see {@link ParticipantCardDto}) — so this is what a post that shares one keeps.
 *
 * @param eventId the event
 * @param carId   the participating car
 */
public record ParticipantCardKey(UUID eventId, UUID carId) {}

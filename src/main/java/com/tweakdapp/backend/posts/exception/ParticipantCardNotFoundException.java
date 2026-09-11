package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when sharing a participant card that does not exist or is not the caller's — the event is
 * not (yet) marked finished, the car was never an accepted participant, or it belongs to someone
 * else. One 404 for all of them, so a card cannot be probed for.
 */
public class ParticipantCardNotFoundException extends NotFoundException {

    public ParticipantCardNotFoundException(UUID eventId, UUID carId) {
        super("No participant card for car " + carId + " at event " + eventId);
    }
}

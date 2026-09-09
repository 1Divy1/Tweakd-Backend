package com.tweakdapp.backend.mapevents;

import java.util.List;
import java.util.UUID;

/**
 * Published when a car owner asks to enter their car into a contest and the request needs an
 * organizer's decision.
 *
 * @param eventId      the event the contest belongs to
 * @param eventTitle   its title, for the notification text
 * @param contestId    the contest
 * @param contestTitle its title
 * @param carId        the car asking in
 * @param actorId      the owner who asked
 * @param recipientIds the individual organizers who can decide, minus the actor
 */
public record ContestEntryRequestedEvent(
        UUID eventId,
        String eventTitle,
        UUID contestId,
        String contestTitle,
        UUID carId,
        UUID actorId,
        List<UUID> recipientIds
) {}

package com.carsocialmedia.backend.mapevents;

import java.util.List;
import java.util.UUID;

/**
 * Published when a car is entered into an event that vets its line-up, so the organizers know there
 * is something waiting for a decision. Not published when the event accepts cars automatically —
 * there would be nothing to act on.
 *
 * @param eventId      the event the car was entered into
 * @param title        its title, for the notification text
 * @param carId        the car awaiting a decision
 * @param actorId      the owner who entered it
 * @param recipientIds the event's individual organizers, minus the actor
 */
public record MapEventCarRegisteredEvent(
        UUID eventId,
        String title,
        UUID carId,
        UUID actorId,
        List<UUID> recipientIds
) {}

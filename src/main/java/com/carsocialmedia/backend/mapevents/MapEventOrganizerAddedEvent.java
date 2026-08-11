package com.carsocialmedia.backend.mapevents;

import java.util.UUID;

/**
 * Published when a user is credited as a co-organizer of an event. Only individual organizers
 * produce this — business accounts have no login, so there is nobody to notify.
 *
 * @param eventId     the event
 * @param title       its title, for the notification text
 * @param actorId     the creator who added them
 * @param recipientId the user who was added
 */
public record MapEventOrganizerAddedEvent(
        UUID eventId,
        String title,
        UUID actorId,
        UUID recipientId
) {}

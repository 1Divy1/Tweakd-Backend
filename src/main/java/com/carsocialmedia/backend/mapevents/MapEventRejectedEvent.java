package com.carsocialmedia.backend.mapevents;

import java.util.UUID;

/**
 * Published when an admin rejects a submitted event. The reason travels with it: the creator is
 * expected to act on it, since editing a rejected event resubmits it.
 *
 * @param eventId     the rejected event
 * @param title       its title, for the notification text
 * @param reason      why it was turned down
 * @param recipientId the creator, who submitted it
 */
public record MapEventRejectedEvent(
        UUID eventId,
        String title,
        String reason,
        UUID recipientId
) {}

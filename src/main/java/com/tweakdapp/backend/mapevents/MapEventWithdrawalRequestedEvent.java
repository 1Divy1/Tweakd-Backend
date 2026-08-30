package com.tweakdapp.backend.mapevents;

import java.util.List;
import java.util.UUID;

/**
 * Published when a participant requests to withdraw from an event, so the organizers know there is a
 * decision waiting for them.
 *
 * @param eventId      the event being withdrawn from
 * @param title        its title, for the notification text
 * @param actorId      the participant requesting the withdrawal
 * @param note         the reason they gave, or {@code null}
 * @param recipientIds the event's individual organizers, minus the actor
 */
public record MapEventWithdrawalRequestedEvent(
        UUID eventId,
        String title,
        UUID actorId,
        String note,
        List<UUID> recipientIds
) {}
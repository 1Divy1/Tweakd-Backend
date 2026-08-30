package com.tweakdapp.backend.mapevents;

import java.util.UUID;

/**
 * Published when an admin approves a submitted event and it goes live on the map.
 *
 * <p>Consumed asynchronously by the {@code notification} module after the approving transaction
 * commits, so a rolled-back approval never notifies. Publishing an event rather than calling
 * {@code notification} directly is what keeps this module free of a dependency back on it.
 *
 * @param eventId     the approved event
 * @param title       its title, for the notification text
 * @param recipientId the creator, who submitted it
 */
public record MapEventApprovedEvent(
        UUID eventId,
        String title,
        UUID recipientId
) {}

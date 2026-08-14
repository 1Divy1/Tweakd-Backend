package com.carsocialmedia.backend.mapevents;

import java.util.UUID;

/**
 * Published when an organizer accepts or rejects a car that was entered into their event.
 *
 * @param eventId     the event the car was entered into
 * @param title       its title, for the notification text
 * @param carId       the car that was decided on
 * @param accepted    {@code true} when the car made the line-up, {@code false} when it was turned down
 * @param reason      why it was turned down; {@code null} when {@code accepted}
 * @param recipientId the car's owner
 */
public record MapEventCarDecidedEvent(
        UUID eventId,
        String title,
        UUID carId,
        boolean accepted,
        String reason,
        UUID recipientId
) {}

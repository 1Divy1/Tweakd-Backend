package com.tweakdapp.backend.mapevents;

import java.util.UUID;

/**
 * Published when an organizer accepts or rejects a car's request to enter a contest.
 *
 * @param eventId      the event the contest belongs to
 * @param eventTitle   its title
 * @param contestId    the contest
 * @param contestTitle its title, for the notification text
 * @param carId        the car decided on
 * @param accepted     {@code true} when the car is now on the ballot
 * @param reason       why it was turned down; {@code null} when {@code accepted}
 * @param recipientId  the car's owner
 */
public record ContestEntryDecidedEvent(
        UUID eventId,
        String eventTitle,
        UUID contestId,
        String contestTitle,
        UUID carId,
        boolean accepted,
        String reason,
        UUID recipientId
) {}

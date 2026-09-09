package com.tweakdapp.backend.mapevents;

import java.util.List;
import java.util.UUID;

/**
 * Published when voting opens on a contest — by the clock, by an organizer opening it early, or
 * lazily by the first vote after {@code opens_at}.
 *
 * @param eventId      the event the contest belongs to
 * @param eventTitle   its title
 * @param contestId    the contest
 * @param contestTitle its title, for the notification text
 * @param recipientIds everyone at the meet who should hear about it — {@code attending} RSVPs
 *                     plus the owners of accepted entries — minus whoever caused the opening
 */
public record ContestOpenedEvent(
        UUID eventId,
        String eventTitle,
        UUID contestId,
        String contestTitle,
        List<UUID> recipientIds
) {}

package com.tweakdapp.backend.mapevents;

import java.util.List;
import java.util.UUID;

/**
 * Published when an organizer marks an event finished — the moment its participant cards come into
 * existence. One recipient per owner, however many cars they brought: the notification points at
 * the event page, which lists every one of their cards.
 *
 * <p>Not published for a cancelled event: it has no cards.
 *
 * @param eventId    the event
 * @param eventTitle its title, for the notification text
 * @param ownerIds   owners of the event's accepted cars, de-duplicated
 */
public record ParticipantCardsReadyEvent(UUID eventId, String eventTitle, List<UUID> ownerIds) {}

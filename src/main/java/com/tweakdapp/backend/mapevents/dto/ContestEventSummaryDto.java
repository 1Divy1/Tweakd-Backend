package com.tweakdapp.backend.mapevents.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The event a contest runs inside, embedded in every {@link ContestDto} so a contest read carries
 * its own context. The app's participant card needs the event title and head-count, and a deep
 * link to a contest arrives without the event page having been read first.
 *
 * <p>Every field here is already public on the event detail read to anyone who can read the
 * contest, and nothing is viewer-scoped, so embedding it widens no access.
 *
 * @param id                 the event id
 * @param title              display title
 * @param coverImageUrl      public cover image URL, or {@code null}
 * @param locationName       human-readable place
 * @param startsAt           when the event starts
 * @param attendeesCount     RSVP count
 * @param attendingCarsCount accepted cars in the line-up
 * @param contestsCount      how many contests the event runs, cancelled ones excluded
 * @param status             {@code upcoming}, {@code live}, {@code previous} (an organizer marked
 *                           it finished — only then do participant cards exist), {@code hidden}
 *                           or {@code canceled}
 */
public record ContestEventSummaryDto(
        UUID id,
        String title,
        String coverImageUrl,
        String locationName,
        Instant startsAt,
        int attendeesCount,
        int attendingCarsCount,
        int contestsCount,
        String status
) {}

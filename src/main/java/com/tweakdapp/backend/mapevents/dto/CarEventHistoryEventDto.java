package com.tweakdapp.backend.mapevents.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The slice of an event a car's history row needs — enough for a tappable row, no organizer
 * roster or viewer state.
 *
 * @param id            event id
 * @param title         event title
 * @param coverImageUrl public cover URL, or {@code null}
 * @param locationName  human-readable place
 * @param startsAt      when it started
 * @param endsAt        when it ended, or {@code null}
 * @param status        {@code live} or {@code previous} (history only lists running or finished events)
 */
public record CarEventHistoryEventDto(
        UUID id,
        String title,
        String coverImageUrl,
        String locationName,
        Instant startsAt,
        Instant endsAt,
        String status
) {}

package com.tweakdapp.backend.garage.dto;

import java.time.Instant;
import java.util.List;

/**
 * One event the car attended, as the public car page shows it. The in-app history row with every
 * id stripped: the public page has nothing to link them to.
 *
 * @param title         the event's title
 * @param coverImageUrl public cover URL, or {@code null}
 * @param locationName  human-readable place
 * @param startsAt      when it started
 * @param status        {@code live} or {@code previous}
 * @param placements    podium places the car took there, rank ascending; usually empty
 */
public record PublicCarEventDto(
        String title,
        String coverImageUrl,
        String locationName,
        Instant startsAt,
        String status,
        List<PublicCarPlacementDto> placements
) {}

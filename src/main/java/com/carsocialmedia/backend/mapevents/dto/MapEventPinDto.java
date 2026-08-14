package com.carsocialmedia.backend.mapevents.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One event as rendered on the map. Deliberately carries everything the <strong>floating widget</strong>
 * needs as well as the marker itself, so tapping a pin costs no round trip — the client already has
 * the title, cover, time and counts. Only "view full event" fetches a {@link MapEventDto} by id.
 *
 * @param id                 event id — pass to {@code GET /api/v1/map-events/{id}} on tap
 * @param title              event title
 * @param categoryId         subcategory id (e.g. {@code car_meet}); stable, safe for icon mapping
 * @param categoryLabel      human-readable subcategory label (e.g. {@code "Car meet"})
 * @param lat                latitude of the event location (WGS84)
 * @param lng                longitude of the event location (WGS84)
 * @param locationName       human-readable place, shown under the title
 * @param coverImageUrl      public URL of the cover image, or {@code null} if none was uploaded
 * @param startsAt           when the event starts
 * @param endsAt             when it ends, or {@code null} if open-ended
 * @param status             lifecycle status ({@code upcoming} / {@code live})
 * @param attendeesCount     how many people have RSVP'd
 * @param attendingCarsCount how many cars are in the line-up
 * @param maxParticipantCapacity cap on accepted cars, or {@code null} for no limit
 */
public record MapEventPinDto(
        UUID id,
        String title,
        String categoryId,
        String categoryLabel,
        double lat,
        double lng,
        String locationName,
        String coverImageUrl,
        Instant startsAt,
        Instant endsAt,
        String status,
        int attendeesCount,
        int attendingCarsCount,
        Integer maxParticipantCapacity
) {}

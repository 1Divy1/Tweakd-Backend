package com.tweakdapp.backend.mapevents.dto;

import java.time.Instant;
import java.util.List;

/**
 * An event as an anonymous visitor sees it at {@code https://web.tweakdapp.com/e/{eventId}}.
 *
 * <p>A <strong>projection</strong> written out by hand, not a trimmed {@link MapEventDto}: a field
 * added to the in-app event tomorrow reaches the open internet only when somebody deliberately adds
 * it here too. Never present: the event id (the visitor already has it in the URL and the page
 * links nowhere by id), organizer row / profile / business ids, the rejection reason, the approval
 * state, the viewer block, R2 object keys, and anyone who is merely attending — the page shows how
 * many are going, never who.
 *
 * @param title              event title
 * @param description        the organizer's free text, or {@code null}
 * @param categoryLabel      subcategory label, e.g. {@code "Car meet"}
 * @param locationName       human-readable place
 * @param lat                latitude (WGS84) — for the "Open in Maps" link
 * @param lng                longitude (WGS84)
 * @param startsAt           when the event starts
 * @param endsAt             when it ends, or {@code null} if open-ended
 * @param coverImageUrl      public cover image URL, or {@code null}
 * @param phase              {@code upcoming}, {@code live} or {@code previous}, derived from the clock
 *                           exactly like the map search does — never {@code canceled} (that is a 410)
 * @param attendeesCount     RSVP head-count
 * @param attendingCarsCount accepted cars in the line-up
 * @param organizers         creator first, then co-organizers
 * @param rules              the organizer's rules, in display order
 */
public record PublicMapEventDto(
        String title,
        String description,
        String categoryLabel,
        String locationName,
        double lat,
        double lng,
        Instant startsAt,
        Instant endsAt,
        String coverImageUrl,
        String phase,
        int attendeesCount,
        int attendingCarsCount,
        List<PublicMapEventOrganizerDto> organizers,
        List<String> rules
) {}

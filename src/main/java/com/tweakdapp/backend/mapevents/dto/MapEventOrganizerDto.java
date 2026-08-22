package com.tweakdapp.backend.mapevents.dto;

import java.util.UUID;

/**
 * One organizer credit on the event page. An organizer is either an app user or a business account,
 * never both — {@link #type} says which, and the id/name/username/image fields are filled from
 * whichever it is.
 *
 * <p>Business organizers are <strong>credit only</strong>: business accounts have no login yet, so
 * a business is displayed and linked but cannot act on the event.
 *
 * @param id           the {@code car_event_organizers} row id — what you pass to remove this organizer
 * @param type         {@code individual} or {@code business}
 * @param role         {@code creator} or {@code organizer}
 * @param referenceId  the profile id or business id this credit points at
 * @param name         display name (individual) or business name; never a username
 * @param username     the individual's {@code @username}; {@code null} for a business organizer
 * @param imageUrl     avatar URL (individual) or logo URL (business); {@code null} if none
 */
public record MapEventOrganizerDto(
        UUID id,
        String type,
        String role,
        UUID referenceId,
        String name,
        String username,
        String imageUrl
) {
    /** {@link #type} value for an app user organizer. */
    public static final String INDIVIDUAL = "individual";

    /** {@link #type} value for a business account organizer. */
    public static final String BUSINESS = "business";
}

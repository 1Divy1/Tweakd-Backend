package com.tweakdapp.backend.mapevents.dto;

/**
 * One organizer credit on the public event page. Same content as {@link MapEventOrganizerDto}
 * minus every id: the page links a person by username, and an internal id on a public page is a
 * handle for scraping and nothing else.
 *
 * @param type     {@code individual} or {@code business}
 * @param role     {@code creator} or {@code organizer}
 * @param name     display name (individual) or business name, or {@code null}
 * @param username the individual's {@code @username}; {@code null} for a business
 * @param imageUrl avatar (individual) or logo (business), or {@code null}
 */
public record PublicMapEventOrganizerDto(
        String type,
        String role,
        String name,
        String username,
        String imageUrl
) {}

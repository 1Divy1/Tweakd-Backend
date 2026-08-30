package com.tweakdapp.backend.mapevents.dto.request;

import java.util.UUID;

/**
 * Adds a co-organizer to an event. Exactly one of the two ids must be set — an organizer is either
 * an app user or a business account, never both.
 *
 * <p>A business organizer is <strong>credit only</strong>: business accounts have no login yet, so
 * it is displayed on the event page but grants no permissions.
 *
 * @param userId     profile id of an app user to add, or {@code null}
 * @param businessId business account id to add, or {@code null}
 */
public record AddOrganizerRequest(
        UUID userId,
        UUID businessId
) {}

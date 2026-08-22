package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Payload for creating a car event. Everything else — id, creator, status, approval state, counts —
 * is server-assigned. The event is submitted as {@code pending} and stays off the map until an
 * admin approves it.
 *
 * <p>The cover image is <em>not</em> part of this payload. The flow mirrors posts and the garage
 * "add car" wizard: the event row is created first so its id exists, then Flutter requests a
 * presigned upload URL ({@code GET /api/storage/events/{eventId}/cover}), uploads straight to R2,
 * and sends the key back via {@code PATCH /api/v1/map-events/{eventId}/cover}.
 *
 * <p>{@code registrationDeadline} is car-meet specific and required when {@code categoryId} is
 * {@code car_meet}. Further subcategories will add their own optional blocks alongside it.
 *
 * @param categoryId                  subcategory id; must be an available {@code car_event_categories} row
 * @param title                       event title
 * @param description                 what the event is about
 * @param locationName                human-readable place shown under the title
 * @param lat                         latitude of the pin (WGS84, -90..90)
 * @param lng                         longitude of the pin (WGS84, -180..180)
 * @param startsAt                    when the event starts; must be in the future
 * @param endsAt                      optional end time; must be after {@code startsAt}
 * @param requiresParticipantApproval whether entered cars need an organizer's approval (default true)
 * @param maxParticipantCapacity      optional cap on accepted cars; {@code null} means no limit
 * @param registrationDeadline        car meets: last moment a car may be entered; must not be after {@code startsAt}
 * @param rules                       optional ordered list of organizer rules, saved in the same
 *                                     transaction as the event; may also be set or replaced later via
 *                                     {@code PUT /{eventId}/rules}
 */
public record CreateMapEventRequest(
        @NotBlank String categoryId,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Size(max = 200) String locationName,
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Instant startsAt,
        Instant endsAt,
        Boolean requiresParticipantApproval,
        @Positive Integer maxParticipantCapacity,
        Instant registrationDeadline,
        @Size(max = 50) List<@NotBlank @Size(max = 300) String> rules
) {}

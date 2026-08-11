package com.carsocialmedia.backend.mapevents.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * An event as it appears in a <em>list</em> — the caller's own events, and the admin review queue.
 *
 * <p>Unlike {@link MapEventDto} it does not resolve the organizer roster, which would cost a query
 * per row; unlike {@link MapEventPinDto} it does carry the approval state, which is the whole point
 * of both screens. Open one to get the full {@link MapEventDto}.
 *
 * @param id                 event id
 * @param title              event title
 * @param categoryId         subcategory id (e.g. {@code car_meet})
 * @param categoryLabel      subcategory label (e.g. {@code "Car meet"})
 * @param locationName       human-readable place
 * @param lat                latitude (WGS84)
 * @param lng                longitude (WGS84)
 * @param startsAt           when the event starts
 * @param endsAt             when it ends, or {@code null} if open-ended
 * @param coverImageUrl      public cover image URL, or {@code null} if none was uploaded yet
 * @param status             upcoming / live / previous / hidden / canceled
 * @param approvalStatus     pending / accepted / rejected
 * @param rejectionReason    why it was rejected, or {@code null}
 * @param attendeesCount     RSVP count
 * @param attendingCarsCount accepted cars in the line-up
 * @param creator            the user who created it — the person an admin is reviewing
 * @param createdAt          when it was submitted; the review queue is ordered by this, oldest first
 */
public record MapEventSummaryDto(
        UUID id,
        String title,
        String categoryId,
        String categoryLabel,
        String locationName,
        double lat,
        double lng,
        Instant startsAt,
        Instant endsAt,
        String coverImageUrl,
        String status,
        String approvalStatus,
        String rejectionReason,
        int attendeesCount,
        int attendingCarsCount,
        ProfileSearchResultDto creator,
        Instant createdAt
) {}

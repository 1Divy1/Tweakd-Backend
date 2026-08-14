package com.carsocialmedia.backend.mapevents.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A car event's full detail page. Attendees and the car line-up are <em>not</em> inlined — both are
 * unbounded and paginated separately ({@code /attendees}, {@code /cars}); the counts here are what
 * the header shows.
 *
 * @param id                            event id
 * @param title                         event title
 * @param description                   free-text description
 * @param categoryId                    subcategory id (e.g. {@code car_meet})
 * @param categoryLabel                 subcategory label (e.g. {@code "Car meet"})
 * @param locationName                  human-readable place
 * @param lat                           latitude (WGS84)
 * @param lng                           longitude (WGS84)
 * @param startsAt                      when the event starts
 * @param endsAt                        when it ends, or {@code null} if open-ended
 * @param coverImageUrl                 public cover image URL, or {@code null}
 * @param status                        lifecycle status: upcoming / live / previous / hidden / canceled
 * @param approvalStatus                pending / accepted / rejected
 * @param rejectionReason               why it was rejected — only ever sent to an organizer
 * @param requiresParticipantApproval   whether entered cars need an organizer's approval
 * @param maxParticipantCapacity        cap on accepted cars, or {@code null} for no limit
 * @param attendeesCount                RSVP count
 * @param attendingCarsCount            accepted cars in the line-up
 * @param organizers                    the creator plus any co-organizers, creator first
 * @param rules                         the organizer's rules for the event, in display order
 * @param carMeet                       car-meet specific detail, or {@code null} for other categories
 * @param viewer                        what the calling user may do here and where they stand
 * @param createdAt                     when the event was submitted
 */
public record MapEventDto(
        UUID id,
        String title,
        String description,
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
        boolean requiresParticipantApproval,
        Integer maxParticipantCapacity,
        int attendeesCount,
        int attendingCarsCount,
        List<MapEventOrganizerDto> organizers,
        List<MapEventRuleDto> rules,
        CarMeetDetailsDto carMeet,
        MapEventViewerStateDto viewer,
        Instant createdAt
) {}

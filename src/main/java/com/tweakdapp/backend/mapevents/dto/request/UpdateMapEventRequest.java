package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial update of an event. Every field is optional; {@code null} means "leave as is". The
 * category cannot be changed — a car meet does not become something else, it gets recreated.
 *
 * <p>Only allowed while the event is <strong>pending or rejected</strong>. Approval locks an event
 * so it cannot be approved as one thing and quietly become another; editing a rejected event clears
 * its rejection reason and resubmits it as pending.
 *
 * <p>{@code lat} and {@code lng} move the pin and must be supplied together.
 *
 * @param title                       new title
 * @param description                 new description
 * @param locationName                new human-readable place
 * @param lat                         new latitude (WGS84); requires {@code lng}
 * @param lng                         new longitude (WGS84); requires {@code lat}
 * @param startsAt                    new start time; must be in the future
 * @param endsAt                      new end time; must be after the (new) start
 * @param requiresParticipantApproval new car-approval toggle
 * @param maxParticipantCapacity      new cap on accepted cars; {@code null} leaves it unchanged
 * @param registrationDeadline        car meets only: new registration deadline
 */
public record UpdateMapEventRequest(
        @Size(max = 120) String title,
        @Size(max = 5000) String description,
        @Size(max = 200) String locationName,
        Double lat,
        Double lng,
        Instant startsAt,
        Instant endsAt,
        Boolean requiresParticipantApproval,
        @Positive Integer maxParticipantCapacity,
        Instant registrationDeadline
) {}

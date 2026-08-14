package com.carsocialmedia.backend.mapevents.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;

import java.time.Instant;

/**
 * One car entered into an event, with the owner reachable through the car summary.
 *
 * @param car            the car (brand, model, cover image, owner)
 * @param status         {@code pending}, {@code accepted}, {@code rejected} or {@code withdrawn}
 * @param registeredAt   when the owner entered the car
 * @param rejectionReason why the organizer turned it down; only set when {@code status} is {@code rejected}
 */
public record MapEventParticipantDto(
        CarSummaryDto car,
        String status,
        Instant registeredAt,
        String rejectionReason
) {}

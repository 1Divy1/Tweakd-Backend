package com.carsocialmedia.backend.mapevents.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;

import java.time.Instant;

/**
 * One car entered into an event, with the owner reachable through the car summary.
 *
 * @param car          the car (brand, model, cover image, owner)
 * @param status       {@code pending}, {@code accepted} or {@code rejected}
 * @param registeredAt when the owner entered the car
 */
public record MapEventParticipantDto(
        CarSummaryDto car,
        String status,
        Instant registeredAt
) {}

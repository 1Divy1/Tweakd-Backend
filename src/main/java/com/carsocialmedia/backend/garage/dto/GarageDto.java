package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GarageDto(
        UUID id,
        UUID ownerId,
        String name,
        Instant createdAt,
        List<CarSummaryDto> cars
) {}
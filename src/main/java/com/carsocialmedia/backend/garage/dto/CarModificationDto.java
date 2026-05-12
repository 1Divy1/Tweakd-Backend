package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.UUID;

public record CarModificationDto(
        UUID id,
        UUID carId,
        String categoryId,
        String categoryName,
        String title,
        String description,
        String beforeImageUrl,
        String afterImageUrl,
        Instant installationDate,
        Float price,
        boolean isPricePublic,
        Integer mileageAtInstall,
        Instant createdAt
) {}
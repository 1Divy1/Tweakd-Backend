package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full car detail. Brand / model / drivetrain / color / mileage unit are returned with
 * both their id (for editing) and a denormalized display label (for rendering).
 */
public record CarDto(
        UUID id,
        UUID garageId,
        UUID brandId,
        String brandName,
        UUID modelId,
        String modelName,
        String drivetrainId,
        String drivetrainName,
        String colorId,
        String colorName,
        String colorCode,
        String mileageUnitId,
        String mileageUnitName,
        int year,
        int horsepower,
        int torque,
        int weight,
        float engineDisplacement,
        Float zeroToOneHundred,
        String chassisCode,
        String engineCode,
        String coverImageUrl,
        Instant createdAt,
        List<CarModificationDto> modifications
) {}
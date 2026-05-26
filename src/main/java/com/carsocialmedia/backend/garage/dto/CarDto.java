package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full car detail view with all specifications and modifications.
 *
 * Reference fields (brand, model, drivetrain, color, mileage unit) are returned with
 * both their ID (for client-side form state) and denormalized display label (for rendering).
 * This redundancy allows the frontend to avoid round-trips for label lookups.
 *
 * @param id the car ID
 * @param garageId the garage this car belongs to
 * @param brandId the car brand ID
 * @param brandName the car brand name
 * @param modelId the car model ID
 * @param modelName the car model name
 * @param drivetrainId the drivetrain type ID
 * @param drivetrainName the drivetrain type name (e.g., FWD, RWD, AWD)
 * @param colorId the color ID
 * @param colorName the color name
 * @param colorCode the hex color code
 * @param mileageUnitId the mileage unit ID
 * @param mileageUnitName the mileage unit name (e.g., km, miles)
 * @param year the year the car was manufactured (1900-2100)
 * @param horsepower engine horsepower
 * @param torque engine torque
 * @param weight car weight in kilograms
 * @param engineDisplacement engine displacement in liters
 * @param zeroToOneHundred 0-100 km/h acceleration time in seconds (nullable)
 * @param chassisCode internal chassis/body code (e.g., F80, F82)
 * @param engineCode internal engine code
 * @param coverImageUrl URL to the car's cover/hero image
 * @param galleryUrls ordered list of gallery image URLs
 * @param createdAt when the car was added to the garage
 * @param status the car's status option (e.g., daily driver, weekend cruiser); nullable
 * @param modifications list of modifications (upgrades) made to this car
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
        List<String> galleryUrls,
        Instant createdAt,
        CarStatusOptionDto status,
        List<CarModificationDto> modifications
) {}
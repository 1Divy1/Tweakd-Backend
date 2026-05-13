package com.carsocialmedia.backend.garage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for creating or fully replacing a car.
 *
 * All required fields must be present on both POST (create) and PUT (update) to maintain
 * row consistency — partial updates are not supported. All numeric values must be positive
 * unless explicitly marked {@code PositiveOrZero}.
 *
 * @param brandId the car brand ID (must exist in car_brands table)
 * @param modelId the car model ID (must exist in car_models table and belong to the selected brand)
 * @param drivetrainId the drivetrain type ID (e.g., FWD, RWD, AWD)
 * @param colorId the paint/body color ID (e.g., Black, Red)
 * @param mileageUnitId the distance unit ID (e.g., kilometers, miles)
 * @param year the manufacturing year (1900-2100)
 * @param horsepower engine horsepower (must be positive)
 * @param torque engine torque in Nm (can be zero for electric cars)
 * @param weight car weight in kilograms
 * @param engineDisplacement engine displacement in liters
 * @param zeroToOneHundred 0-100 km/h acceleration time in seconds (nullable)
 * @param chassisCode internal chassis/body code (e.g., F80, F82), max 50 chars
 * @param engineCode internal engine code (e.g., S65B40, M340i), max 50 chars
 * @param coverImageUrl URL to the car's cover/hero image (must be non-blank)
 * @param statusId the car status option ID (optional, e.g., daily driver, weekend cruiser)
 */
public record CarRequest(
        @NotNull UUID brandId,
        @NotNull UUID modelId,
        @NotBlank String drivetrainId,
        @NotBlank String colorId,
        @NotBlank String mileageUnitId,

        @Min(1900) @Max(2100) int year,
        @Positive int horsepower,
        @PositiveOrZero int torque,
        @Positive int weight,
        @Positive float engineDisplacement,

        @Positive Float zeroToOneHundred,

        @Size(max = 50) String chassisCode,
        @Size(max = 50) String engineCode,

        @NotBlank String coverImageUrl,

        @NotBlank String statusId
) {}

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
 * Payload for creating or fully replacing a car. PUT-style semantics — all required fields
 * must be present on both create and update so the row stays consistent.
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

        @NotBlank String coverImageUrl
) {}

package com.carsocialmedia.backend.garage.dto;

/**
 * Result of adding a modification to an existing car.
 *
 * @param modification the created modification with all references resolved
 */
public record AddModificationResponse(
        CarModificationDto modification
) {}
package com.carsocialmedia.backend.garage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Single-shot payload for the "add car" wizard.
 *
 * @param car the car specifications
 * @param modifications the modifications to create alongside the car (may be empty)
 */
public record CreateCarRequest(
        @NotNull @Valid CarRequest car,
        @NotNull @Valid List<CarModificationRequest> modifications
) {}
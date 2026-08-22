package com.tweakdapp.backend.garage.dto.response;

import com.tweakdapp.backend.garage.dto.CarDto;

/**
 * Result of the single-shot "add car" submission.
 *
 * @param car the created car, with all references resolved and modifications embedded
 */
public record CreateCarResponse(
        CarDto car
) {}

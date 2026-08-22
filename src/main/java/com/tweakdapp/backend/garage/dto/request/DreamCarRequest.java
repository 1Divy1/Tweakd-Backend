package com.tweakdapp.backend.garage.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload for creating one or more dream cars. The dream cars are always sent
 * inside a list, even when there is a single one.
 *
 * @param dreamCars the dream cars to create (at least one). Each must have a brand ID and may have a model ID.
 */
public record DreamCarRequest(
        @NotEmpty @Valid List<DreamCarRequestBody> dreamCars
) {}
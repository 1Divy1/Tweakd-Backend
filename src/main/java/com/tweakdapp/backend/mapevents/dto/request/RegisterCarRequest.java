package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Enters one of the caller's own cars into an event. Rejected if the car belongs to somebody else
 * or the category's registration deadline has passed.
 *
 * @param carId the car to enter; must be owned by the caller
 */
public record RegisterCarRequest(
        @NotNull UUID carId
) {}

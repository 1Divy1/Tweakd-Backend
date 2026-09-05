package com.tweakdapp.backend.garage.dto;

import java.util.UUID;

/**
 * What the app gets back when it resolves a scanned or tapped share code: enough to navigate to the
 * existing native car screen, and nothing more. The public DTO is for the website; an installed app
 * wants its own car view, not a second rendering of the same data.
 *
 * @param carId         the car to open at {@code /garage/cars/{carId}}
 * @param ownerUsername the owner's username, so the app can show whose build it is while loading
 */
public record CarShareResolutionDto(UUID carId, String ownerUsername) {}

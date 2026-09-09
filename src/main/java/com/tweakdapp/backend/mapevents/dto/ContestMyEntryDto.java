package com.tweakdapp.backend.mapevents.dto;

import java.util.UUID;

/**
 * Where one of the caller's own cars stands in a contest.
 *
 * @param carId           the car
 * @param status          {@code pending}, {@code accepted}, {@code rejected} or {@code withdrawn}
 * @param rejectionReason why an organizer turned it down; only with {@code rejected}
 */
public record ContestMyEntryDto(UUID carId, String status, String rejectionReason) {}

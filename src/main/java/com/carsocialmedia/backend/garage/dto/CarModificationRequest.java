package com.carsocialmedia.backend.garage.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Payload for creating or fully replacing a car modification. The
 * {@code is_price_public} / {@code price} pairing mirrors the Supabase
 * {@code car_modifications_price_visibility_check} constraint: if the price is marked
 * public, it must be present.
 */
public record CarModificationRequest(
        @NotBlank String categoryId,

        @NotBlank @Size(max = 100) String title,

        @Size(max = 1000) String description,

        @NotBlank String beforeImageUrl,
        @NotBlank String afterImageUrl,

        @NotNull Instant installationDate,

        @Positive Float price,

        boolean isPricePublic,

        @Positive Integer mileageAtInstall
) {
        @AssertTrue(message = "price must be set when isPricePublic is true")
        public boolean isPriceConsistent() {
                return !isPricePublic || price != null;
        }
}

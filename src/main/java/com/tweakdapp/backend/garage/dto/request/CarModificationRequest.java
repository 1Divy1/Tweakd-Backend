package com.tweakdapp.backend.garage.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for creating or fully replacing a car modification.
 *
 * All required fields must be present on both POST (create) and PUT (update) to maintain
 * consistency.
 *
 * @param categoryId the modification category ID (e.g., suspension, engine, wheels)
 * @param title short title describing the modification (max 100 chars)
 * @param description detailed description of the modification (max 1000 chars)
 * @param installationDate when the modification was installed on the car
 * @param price the cost of the modification in the car owner's currency
 * @param mileageAtInstall the car's mileage reading when the modification was installed
 */
public record CarModificationRequest(
        @NotBlank String categoryId,

        @NotBlank @Size(max = 100) String title,

        @Size(max = 1000) String description,

        @NotNull Instant installationDate,

        @Positive Integer price,

        @Positive Integer mileageAtInstall
) {}

package com.carsocialmedia.backend.garage.dto;

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
 * consistency. The {@code isPricePublic} / {@code price} pairing is validated by
 * {@code isPriceConsistent()}: if the price is marked public, the price must be provided.
 *
 * @param categoryId the modification category ID (e.g., suspension, engine, wheels)
 * @param title short title describing the modification (max 100 chars)
 * @param description detailed description of the modification (max 1000 chars)
 * @param beforeImageUrl URL to the before/original state image
 * @param afterImageUrl URL to the after/modified state image
 * @param installationDate when the modification was installed on the car
 * @param price the cost of the modification in the car owner's currency (required if
 *        {@code isPricePublic} is true, otherwise optional)
 * @param isPricePublic whether other users can see the modification cost
 * @param mileageAtInstall the car's mileage reading when the modification was installed
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
        /**
         * Validates that if the price is marked public, the price value is provided.
         *
         * @return true if the price visibility constraint is satisfied
         */
        @AssertTrue(message = "price must be set when isPricePublic is true")
        public boolean isPriceConsistent() {
                return !isPricePublic || price != null;
        }
}

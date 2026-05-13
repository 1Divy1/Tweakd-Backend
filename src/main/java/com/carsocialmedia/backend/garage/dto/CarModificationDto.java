package com.carsocialmedia.backend.garage.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A modification (upgrade, customization, or change) made to a car.
 *
 * Price visibility is controlled by the {@code isPricePublic} flag. When viewing a
 * modification on a car that is not yours, the price will be null if the owner marked
 * it as private.
 *
 * @param id the modification ID
 * @param carId the car this modification belongs to
 * @param categoryId the modification category ID (e.g., suspension, engine)
 * @param categoryName the modification category name
 * @param title short title of the modification (e.g., "H&R Coilovers")
 * @param description detailed description of the modification (up to 1000 chars)
 * @param beforeImageUrl URL to the before/original state image
 * @param afterImageUrl URL to the after/modified state image
 * @param installationDate when the modification was installed
 * @param price the cost of the modification in the car owner's currency (null if private
 *        and viewer is not the owner)
 * @param isPricePublic whether the modification price is visible to other users
 * @param mileageAtInstall the car's mileage when the modification was installed
 * @param createdAt when this modification record was created
 */
public record CarModificationDto(
        UUID id,
        UUID carId,
        String categoryId,
        String categoryName,
        String title,
        String description,
        String beforeImageUrl,
        String afterImageUrl,
        Instant installationDate,
        Float price,
        boolean isPricePublic,
        Integer mileageAtInstall,
        Instant createdAt
) {}
package com.tweakdapp.backend.garage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A modification (upgrade, customization, or change) made to a car.
 *
 * @param id the modification ID
 * @param carId the car this modification belongs to
 * @param categoryId the modification category ID (e.g., suspension, engine)
 * @param categoryName the modification category name
 * @param title short title of the modification (e.g., "H&R Coilovers")
 * @param description detailed description of the modification (up to 1000 chars)
 * @param media all before/after images and videos attached to this modification
 * @param installationDate when the modification was installed
 * @param price the cost of the modification in the car owner's currency (null if not set)
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
        List<CarModificationMediaDto> media,
        Instant installationDate,
        Integer price,
        Integer mileageAtInstall,
        Instant createdAt
) {}
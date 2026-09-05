package com.tweakdapp.backend.garage.dto;

import java.time.Instant;
import java.util.List;

/**
 * One modification on the public car page. The build list is the reason anyone scans the sticker,
 * so it keeps its detail — including price, which is already visible to every app user.
 *
 * <p>No ids: neither the modification's nor the car's. Both are internal, and a public page has
 * nothing to do with them.
 *
 * @param categoryName     the category's display name (e.g. "Suspension")
 * @param title            what was fitted (e.g. "H&R Coilovers")
 * @param description      the owner's write-up
 * @param media            before/after photos and videos
 * @param installationDate when it went on the car
 * @param price            what it cost, null if the owner did not record one
 * @param priceCurrency    the currency of {@code price}
 * @param mileageAtInstall the car's mileage at the time
 */
public record PublicCarModificationDto(
        String categoryName,
        String title,
        String description,
        List<PublicMediaDto> media,
        Instant installationDate,
        Integer price,
        String priceCurrency,
        Integer mileageAtInstall
) {}

package com.tweakdapp.backend.garage.dto;

import java.time.Instant;
import java.util.List;

/**
 * A car as an anonymous visitor sees it at {@code https://web.tweakdapp.com/c/{code}}.
 *
 * <p>This is a <strong>projection</strong> of {@link CarDto}, not a rename of it, and that is the
 * point: it lists what is public by hand, so a field added to the car tomorrow is exposed to the
 * open internet only when somebody deliberately adds it here too. Never present, whatever
 * {@code CarDto} grows: the licence plate, the owner's UUID, the garage id, the car id, R2 object
 * keys, and the owner's location.
 *
 * @param code           the share code this page was reached by
 * @param url            the canonical URL of this page, for {@code og:url} and the copy button
 * @param sharedAt       when the link was first created
 */
public record PublicCarDto(
        String code,
        String url,
        String brandName,
        String modelName,
        int year,
        String modelCode,
        String chassisCode,
        String engineCode,
        int horsepower,
        int torque,
        int weight,
        float engineDisplacement,
        Float zeroToOneHundred,
        String drivetrainName,
        String colorName,
        String colorCode,
        String fuelTypeName,
        String statusName,
        Integer mileage,
        String mileageUnitName,
        String story,
        String coverImageUrl,
        List<String> galleryUrls,
        List<PublicCarModificationDto> modifications,
        PublicCarOwnerDto owner,
        Instant sharedAt
) {}

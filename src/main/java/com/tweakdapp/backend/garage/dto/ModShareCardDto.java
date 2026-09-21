package com.tweakdapp.backend.garage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A build-log modification as it is drawn in the feed, when a post shares one.
 *
 * Like a participant card, this is <em>derived on every read</em> and never stored: the post holds
 * only the modification's id. An edited mod therefore updates wherever it was shared, and a post
 * can never show a mod that no longer says what it claims.
 *
 * <p>It is a hand-written projection rather than a reuse of {@link CarModificationDto}: the feed is
 * seen by everyone, so a field added to the build log next year reaches it only if somebody
 * deliberately adds it here. That is also why {@code price} is masked upstream — it is present only
 * when the owner set {@code isPricePublic}.
 *
 * @param modificationId   the modification, so the card can open the car's build log
 * @param car              the car it went on, for the card's title line and its tap target
 * @param categoryName     the category's display name (e.g. "Suspension")
 * @param title            what was fitted (e.g. "H&R Coilovers")
 * @param description      the owner's write-up, null if they wrote none
 * @param beforeMedia      photos of the car before the mod, in insertion order
 * @param afterMedia       photos of the car after it
 * @param installationDate when it went on the car
 * @param price            what it cost — null unless the owner publishes it
 * @param priceCurrency    the currency of {@code price}, null when {@code price} is
 * @param mileageAtInstall the car's mileage at the time, null if not recorded
 */
public record ModShareCardDto(
        UUID modificationId,
        CarSummaryDto car,
        String categoryName,
        String title,
        String description,
        List<CarModificationMediaDto> beforeMedia,
        List<CarModificationMediaDto> afterMedia,
        Instant installationDate,
        Integer price,
        String priceCurrency,
        Integer mileageAtInstall
) {}

package com.carsocialmedia.backend.business.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A business's full profile page — everything shown after tapping its pin on the map.
 *
 * <p>Only active, verified businesses are ever returned, so no status fields are exposed: their
 * presence in a response already means "live and approved".
 *
 * @param id            the business id
 * @param name          display name
 * @param typeId        business type id (e.g. {@code tuning_shop})
 * @param typeLabel     human-readable type label (e.g. {@code "Tuning shop"})
 * @param description   free-text description, or {@code null}
 * @param logoUrl       logo image URL, or {@code null}
 * @param address       street address as entered by the business
 * @param cityId        id of the city the business sits in ({@code cities.id})
 * @param cityName      display name of that city, or {@code null} if the id no longer resolves
 * @param lat           latitude of the business location (WGS84)
 * @param lng           longitude of the business location (WGS84)
 * @param phoneNumber   contact phone, or {@code null}
 * @param email         contact email, or {@code null}
 * @param websiteUrl    website, or {@code null}
 * @param averageRating average review score
 * @param reviewCount   number of reviews the average is based on
 * @param followerCount number of app users following this business
 * @param timezone      IANA zone id the opening hours are expressed in (e.g. {@code Europe/Bucharest})
 * @param isOpenNow     whether the business is open at request time, derived from {@code hours}
 * @param hours         the weekly schedule, Monday first; may be empty if none has been set
 * @param verifiedAt    when a moderator approved this business
 * @param createdAt     when the business was created
 */
public record BusinessDto(
        UUID id,
        String name,
        String typeId,
        String typeLabel,
        String description,
        String logoUrl,
        String address,
        String cityId,
        String cityName,
        double lat,
        double lng,
        String phoneNumber,
        String email,
        String websiteUrl,
        double averageRating,
        int reviewCount,
        int followerCount,
        String timezone,
        boolean isOpenNow,
        List<BusinessHoursDto> hours,
        Instant verifiedAt,
        Instant createdAt
) {}

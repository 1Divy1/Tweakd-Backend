package com.tweakdapp.backend.business.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A business as the <strong>dashboard</strong> sees it: everything {@link BusinessDto} carries,
 * plus the status fields the app is deliberately never shown.
 *
 * <p>A separate DTO precisely so those fields cannot leak into an app response. {@code BusinessDto}
 * exposes no status at all — only active, verified businesses ever reach it, so a status field
 * there would be both dead weight and a way to learn that a suspended or rejected business exists.
 * Everything here is readable only behind {@code VERIFY_BUSINESSES}.
 *
 * @param verificationStatus {@code pending}, {@code verified} or {@code rejected}
 * @param activeStatus       {@code active}, {@code suspended} or {@code deleted}
 * @param rejectionReason    why the last rejection happened, or {@code null}
 * @param verifiedAt         when it was approved, or {@code null}
 * @param reviewedBy         staff auth user id of the last decision — <em>not</em> a profile id,
 *                           so never render it with profile components
 * @param reviewedAt         when that decision was taken
 * @param isOpenNow          derived from {@code hours} in the business's own timezone, as in the app
 */
public record AdminBusinessDto(
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
        String verificationStatus,
        String activeStatus,
        String rejectionReason,
        Instant verifiedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {}

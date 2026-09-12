package com.tweakdapp.backend.business.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the dashboard's business review queue. Deliberately thinner than
 * {@link AdminBusinessDto}: a list page must not pay for opening hours, city lookups or the
 * "open now" computation, none of which a reviewer reads before opening the row.
 */
public record AdminBusinessSummaryDto(
        UUID id,
        String name,
        String typeId,
        String typeLabel,
        String logoUrl,
        String address,
        String cityId,
        String verificationStatus,
        String activeStatus,
        String rejectionReason,
        Instant verifiedAt,
        Instant createdAt
) {}

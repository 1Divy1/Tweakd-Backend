package com.carsocialmedia.backend.admin.internal.dto;

import java.util.List;

/** One keyset page of the moderation queue. {@code nextCursor} is {@code null} on the last page. */
public record CasePageDto(
        List<CaseSummaryDto> items,
        String nextCursor) {
}

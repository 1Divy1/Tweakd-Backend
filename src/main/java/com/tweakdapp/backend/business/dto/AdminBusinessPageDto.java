package com.tweakdapp.backend.business.dto;

import java.util.List;

/**
 * One keyset page of the review queue.
 *
 * @param items      the page, oldest submission first — nothing waits indefinitely
 * @param nextCursor token to pass back as {@code ?cursor=}, or {@code null} on the last page
 */
public record AdminBusinessPageDto(
        List<AdminBusinessSummaryDto> items,
        String nextCursor
) {}

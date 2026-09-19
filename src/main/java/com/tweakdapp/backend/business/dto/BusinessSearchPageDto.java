package com.tweakdapp.backend.business.dto;

import java.util.List;

/**
 * One keyset page of the map's business search.
 *
 * @param items      matching businesses, nearest to the searched-around centre first
 * @param nextCursor token to pass back as {@code ?cursor=} (with the same {@code lat}/{@code lng}),
 *                   or {@code null} on the last page
 */
public record BusinessSearchPageDto(
        List<BusinessMapPinDto> items,
        String nextCursor
) {}

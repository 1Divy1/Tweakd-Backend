package com.carsocialmedia.backend.support.dto;

import java.util.List;

/** One keyset page of ticket summaries, most recently active first. */
public record TicketPageDto(
        List<TicketSummaryDto> items,
        String nextCursor
) {}

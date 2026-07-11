package com.carsocialmedia.backend.support.dto;

/** One category a user may pick when opening a ticket. */
public record TicketCategoryDto(
        String id,
        String name
) {}

package com.carsocialmedia.backend.support.dto;

/**
 * Headline numbers for the dashboard's Tickets page. {@code open} = waiting on staff,
 * {@code awaitingUser} = staff replied last, {@code resolvedToday} = tickets resolved since midnight
 * UTC. (Median first-response time is a deferred metric — see ADMIN_DASHBOARD_PROGRESS.md.)
 */
public record TicketStatsDto(
        long open,
        long awaitingUser,
        long resolvedToday
) {}

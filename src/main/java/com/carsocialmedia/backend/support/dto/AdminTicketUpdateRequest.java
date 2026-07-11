package com.carsocialmedia.backend.support.dto;

import java.util.UUID;

/**
 * Partial update applied from the dashboard: any non-null field is applied. {@code assigneeId} is a
 * staff profile UUID; {@code unassign} clears the assignee (a null {@code assigneeId} alone means
 * "leave unchanged").
 */
public record AdminTicketUpdateRequest(
        String priority,
        String status,
        UUID assigneeId,
        Boolean unassign
) {}

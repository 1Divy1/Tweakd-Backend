package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Requests withdrawal from an event. This does not remove the caller's cars from the line-up: every
 * one of their {@code accepted} rows for the event is flagged {@code withdrawn} and stays visible
 * until an organizer approves the request (removed) or rejects it (reverts to {@code accepted}).
 *
 * @param note optional context for the organizer reviewing the request
 */
public record WithdrawParticipationRequest(
        @Size(max = 1000) String note
) {}
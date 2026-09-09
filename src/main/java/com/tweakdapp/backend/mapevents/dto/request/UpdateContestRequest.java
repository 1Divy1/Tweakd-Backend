package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial update of a contest. A {@code null} field is left unchanged (there is no way to clear
 * {@code criteria}; send an empty string for that).
 *
 * <p>While the contest is {@code scheduled} anything may change. Once {@code open}, only
 * {@code closesAt} (moving the planned end) and {@code criteria} are accepted. Both times are
 * planned labels: to actually start voting, call the open endpoint.
 */
public record UpdateContestRequest(
        @Size(min = 3, max = 60) String title,
        @Size(max = 300) String criteria,
        Instant opensAt,
        Instant closesAt
) {}

package com.tweakdapp.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Payload for creating a contest inside an event. Status, counts and the creator are
 * server-assigned; the contest is always published as {@code scheduled} — cars can ask to enter
 * right away, but voting waits for the organizer to open it.
 *
 * @param categoryId a {@code car_event_contest_categories} code
 * @param title      the title attendees see; for predefined categories the app pre-fills the label
 * @param criteria   optional one- or two-line judging note shown above the leaderboard
 * @param opensAt    when voting is <em>planned</em> to open. Shown to attendees; it does not open
 *                   the contest — an organizer does
 * @param closesAt   when voting is <em>planned</em> to close, likewise a label. Must be after
 *                   {@code opensAt} and no later than 12 hours after the event ends
 */
public record CreateContestRequest(
        @NotBlank String categoryId,
        @NotBlank @Size(min = 3, max = 60) String title,
        @Size(max = 300) String criteria,
        @NotNull Instant opensAt,
        @NotNull Instant closesAt
) {}

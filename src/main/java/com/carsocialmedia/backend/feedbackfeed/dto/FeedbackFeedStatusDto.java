package com.carsocialmedia.backend.feedbackfeed.dto;

/**
 * A roadmap stage — {@code sent}, {@code under_development} or {@code completed} — with its display
 * label. Backs the dashboard's status picker and the "in progress" / "completed" pills on a card.
 *
 * @param id    the stable id stored on the message
 * @param label the human-readable name ("Sent", "Under development", "Completed")
 */
public record FeedbackFeedStatusDto(
        String id,
        String label
) {}

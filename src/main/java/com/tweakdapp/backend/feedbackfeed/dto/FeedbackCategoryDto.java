package com.tweakdapp.backend.feedbackfeed.dto;

/**
 * A feedback category — {@code bug}, {@code feature_request} or {@code feature_improvement} — with
 * its display label. Backs the category chips on the compose screen and the pill on each card.
 *
 * @param id    the stable id stored on the message
 * @param label the human-readable name ("Bug", "Feature request", "Feature improvement")
 */
public record FeedbackCategoryDto(
        String id,
        String label
) {}

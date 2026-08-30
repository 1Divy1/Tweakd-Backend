package com.tweakdapp.backend.feedback.dto;

import java.util.Map;

/**
 * Aggregates for the admin dashboard's Feedback page: the four category cards, the by-category
 * donut, the by-status list, and the "new this week" headline. Keys of the maps are option ids
 * (e.g. {@code feature}, {@code under_review}).
 */
public record FeedbackStatsDto(
        long total,
        long newThisWeek,
        Map<String, Long> byType,
        Map<String, Long> byStatus
) {}

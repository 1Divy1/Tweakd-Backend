package com.carsocialmedia.backend.feedback.dto;

/**
 * The lifecycle state of a piece of feedback, resolved from {@code feedback_status_options} so the
 * client can render a status chip without a second lookup.
 *
 * @param id    the {@code feedback_status_options} row id (e.g. {@code submitted}, {@code resolved})
 * @param name  the human-readable status label (e.g. "Submitted", "Resolved")
 * @param color optional hex color for the chip, or {@code null} if none is configured
 */
public record FeedbackStatusDto(String id, String name, String color) {
}

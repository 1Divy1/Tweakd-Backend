package com.tweakdapp.backend.feedback.dto;

/**
 * One feedback category, for populating the type picker in the feedback UI.
 *
 * @param id   the {@code feedback_type_options} row id — sent back as {@code type} when submitting
 * @param type the human-readable category label (e.g. "Bug", "Feature", "General")
 */
public record FeedbackTypeDto(String id, String type) {
}

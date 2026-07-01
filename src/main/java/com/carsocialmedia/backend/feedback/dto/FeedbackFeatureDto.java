package com.carsocialmedia.backend.feedback.dto;

/**
 * One selectable app feature, for populating the feature picker in the feedback UI.
 *
 * @param id   the {@code feedback_feature_options} row id — sent back as {@code feature} when submitting
 * @param name the human-readable feature name
 */
public record FeedbackFeatureDto(String id, String name) {
}

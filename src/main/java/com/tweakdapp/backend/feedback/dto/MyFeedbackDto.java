package com.tweakdapp.backend.feedback.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One piece of feedback the current user has submitted, for the "my feedback" section.
 *
 * <p>Self-contained: {@code type} and {@code feature} are resolved to their human-readable labels,
 * and {@code status} is resolved to a small {@link FeedbackStatusDto} (label + chip color), so the UI
 * can render the entry without a second round-trip to the reference-option lists.
 *
 * @param id                the feedback row id
 * @param content           the feedback message
 * @param type              the resolved feedback type label (never {@code null})
 * @param feature           the resolved feature label, or {@code null} if none was attached
 * @param reproductionSteps the reproduction steps, or {@code null} if none were given
 * @param response          a moderator's reply, or {@code null} if none yet
 * @param status            the current lifecycle state (id, label, color)
 * @param createdAt         when the feedback was submitted
 */
public record MyFeedbackDto(
        UUID id,
        String content,
        String type,
        String feature,
        String reproductionSteps,
        String response,
        FeedbackStatusDto status,
        Instant createdAt) {
}

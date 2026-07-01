package com.carsocialmedia.backend.feedback;

import com.carsocialmedia.backend.feedback.dto.FeedbackFeatureDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackTypeDto;
import com.carsocialmedia.backend.feedback.dto.MyFeedbackDto;

import java.util.List;
import java.util.UUID;

/**
 * Persistence helper for user-submitted app feedback (bug reports, feature requests, general notes).
 *
 * <p>This module owns the {@code feedback} table plus the two reference tables that back the pickers
 * ({@code feedback_type_options}, {@code feedback_feature_options}). It is a dependency-free leaf:
 * feedback references a submitting user only by their flat {@code profiles.id} UUID (the JWT subject),
 * so — unlike moderation reports — it needs no cross-module lookup and owns all of its own endpoints.
 *
 * <p>Validation kept deliberately light: {@code content} and {@code type} are required, and both
 * {@code type} and (when present) {@code feature} must reference an existing reference-table row.
 * {@code feature} may be {@code null} (e.g. feedback about something that isn't a listed feature yet)
 * and {@code reproductionSteps} is optional (the mobile client only collects it for the {@code bug}
 * type, but the backend does not couple itself to that rule).
 */
public interface FeedbackService {

    /**
     * Stores one piece of feedback submitted by a user.
     *
     * @param userId  the submitting user's profile UUID (the JWT subject — assumed to exist)
     * @param request the feedback payload; {@code content} and {@code type} are required, while
     *                {@code feature} and {@code reproductionSteps} are optional
     * @throws com.carsocialmedia.backend.feedback.exception.InvalidFeedbackTypeException if
     *         {@code type} does not reference a {@code feedback_type_options} row
     * @throws com.carsocialmedia.backend.feedback.exception.InvalidFeedbackFeatureException if
     *         {@code feature} is given but does not reference a {@code feedback_feature_options} row
     */
    void submitFeedback(UUID userId, FeedbackRequest request);

    /** The feedback categories a user may pick from (bug / feature / general). */
    List<FeedbackTypeDto> listFeedbackTypes();

    /** The app features a user may attach feedback to. */
    List<FeedbackFeatureDto> listFeedbackFeatures();

    /**
     * Lists every piece of feedback the given user has submitted, newest first, for their own
     * "my feedback" view. Always scoped to {@code userId}, so a caller only ever sees their own.
     *
     * @param userId the submitting user's profile UUID (the JWT subject)
     */
    List<MyFeedbackDto> listMyFeedback(UUID userId);
}

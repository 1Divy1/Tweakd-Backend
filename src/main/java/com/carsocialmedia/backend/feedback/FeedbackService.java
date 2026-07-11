package com.carsocialmedia.backend.feedback;

import com.carsocialmedia.backend.feedback.dto.AdminFeedbackDto;
import com.carsocialmedia.backend.feedback.dto.AdminFeedbackPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackBoardItemDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackBoardPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackCommentDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackCommentPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackCommentRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackFeatureDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatsDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusChangeDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackTypeDto;
import com.carsocialmedia.backend.feedback.dto.MyFeedbackDto;

import java.util.List;
import java.util.UUID;

/**
 * Persistence helper for user-submitted app feedback (bug reports, feature requests, general notes),
 * the public feedback board (votes, comments, subscriptions), and the admin-dashboard operations
 * over it (official responses, status lifecycle).
 *
 * <p>This module owns the {@code feedback} table, its board side-tables ({@code feedback_votes},
 * {@code feedback_comments}, {@code feedback_subscriptions}), and the reference tables that back the
 * pickers and the status lifecycle. Feedback references its author by a flat {@code profiles.id}
 * UUID; the board views resolve author cards through the {@code profile} module's public API.
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

    /** The status lifecycle options (id, label, color), in roadmap order — for chips and pickers. */
    List<FeedbackStatusDto> listFeedbackStatuses();

    // ------------------------------------------------------------------
    // Public feedback board
    // ------------------------------------------------------------------

    /**
     * One keyset page of the public feedback board.
     *
     * @param viewerId the requesting user (for the per-item voted/subscribed flags)
     * @param sort {@code top} (most upvoted), {@code new}, or {@code trending} (most votes in the
     *        last 7 days); anything else falls back to {@code top}
     * @param type optional {@code feedback_type_options} filter, {@code null} = all
     * @param status optional {@code feedback_status_options} filter, {@code null} = all
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max items (clamped to a sane maximum)
     * @throws com.carsocialmedia.backend.feedback.exception.InvalidFeedbackCursorException if the
     *         cursor cannot be decoded
     */
    FeedbackBoardPageDto getBoard(UUID viewerId, String sort, String type, String status, String cursor, int size);

    /**
     * One feedback entry with the viewer's flags — the board detail view.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if it doesn't exist
     */
    FeedbackBoardItemDto getBoardItem(UUID viewerId, UUID feedbackId);

    /**
     * Upvotes a feedback on behalf of the user. Idempotent — voting twice is a no-op. The
     * {@code vote_count} is maintained by a Supabase trigger.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if it doesn't exist
     */
    void vote(UUID userId, UUID feedbackId);

    /** Removes the user's upvote. Idempotent — removing a non-existent vote is a no-op. */
    void unvote(UUID userId, UUID feedbackId);

    /**
     * One keyset page of a feedback's comments, newest first.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if the feedback doesn't exist
     */
    FeedbackCommentPageDto getComments(UUID viewerId, UUID feedbackId, String cursor, int size);

    /**
     * Adds a comment under a feedback entry. The {@code comment_count} is maintained by a Supabase
     * trigger.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if the feedback doesn't exist
     */
    FeedbackCommentDto addComment(UUID userId, UUID feedbackId, FeedbackCommentRequest request);

    /**
     * Deletes the user's own comment.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackCommentNotFoundException if the
     *         comment doesn't exist or belongs to a different feedback
     * @throws com.carsocialmedia.backend.feedback.exception.NotFeedbackCommentAuthorException if the
     *         caller isn't the comment's author
     */
    void deleteComment(UUID userId, UUID feedbackId, UUID commentId);

    /**
     * Subscribes the user to in-app notifications about this feedback's status changes. Idempotent.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if it doesn't exist
     */
    void subscribe(UUID userId, UUID feedbackId);

    /** Removes the user's subscription. Idempotent. */
    void unsubscribe(UUID userId, UUID feedbackId);

    // ------------------------------------------------------------------
    // Admin dashboard (called by the admin module; no auth logic here)
    // ------------------------------------------------------------------

    /**
     * One keyset page of all feedback for the admin dashboard. Same sorts/filters as the public
     * board, plus admin-only fields on each item.
     */
    AdminFeedbackPageDto listAllFeedback(String sort, String type, String status, String cursor, int size);

    /** Aggregates for the dashboard's Feedback page (totals, new this week, by type, by status). */
    FeedbackStatsDto getStats();

    /**
     * Writes (or replaces) the team's official response shown on the board.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if it doesn't exist
     */
    AdminFeedbackDto respond(UUID feedbackId, String response);

    /**
     * Moves a feedback to a new lifecycle status and reports who should be notified (author +
     * subscribers, de-duplicated). The caller (admin module) is responsible for actually pushing
     * the notifications — this module does not depend on {@code notification}.
     *
     * @throws com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException if it doesn't exist
     * @throws com.carsocialmedia.backend.feedback.exception.InvalidFeedbackStatusException if
     *         {@code statusId} doesn't reference a {@code feedback_status_options} row
     */
    FeedbackStatusChangeDto updateStatus(UUID feedbackId, String statusId);
}

package com.carsocialmedia.backend.feedbackfeed;

import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackCategoryDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedPageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedStatusDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackMessageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.request.SubmitFeedbackMessageRequest;

import java.util.List;
import java.util.UUID;

/**
 * The Feedback Feed module's public API — the community feedback board and the staff operations
 * over it.
 *
 * <p>The admin methods carry <strong>no authorization of their own</strong>. The {@code admin}
 * module owns the dashboard endpoints and gates them on the {@code MANAGE_ROADMAP} capability,
 * exactly as it does for map-event approvals.
 */
public interface FeedbackFeedService {

    // ------------------------------------------------------------------
    // Reference data
    // ------------------------------------------------------------------

    /** The categories a user may file feedback under, for the compose screen's chips. */
    List<FeedbackCategoryDto> listTypes();

    /** The roadmap stages, in order, for the status pills and the dashboard's picker. */
    List<FeedbackFeedStatusDto> listStatuses();

    // ------------------------------------------------------------------
    // Reading the feed
    // ------------------------------------------------------------------

    /**
     * One page of the main community feed: everything that has not shipped and has not been removed
     * by staff.
     *
     * @param sort   {@code newest}, {@code popular} (highest net votes) or {@code oldest}
     * @param cursor an opaque token from a previous page of the <em>same</em> sort, or {@code null}
     */
    FeedbackFeedPageDto getFeed(UUID currentUserId, String sort, String cursor, int size);

    /**
     * One page of the "Completed requests" section — shipped messages, most recently shipped first.
     * These are excluded from {@link #getFeed} and can no longer be voted on.
     */
    FeedbackFeedPageDto getCompleted(UUID currentUserId, String cursor, int size);

    /** A single message with the caller's own vote resolved. */
    FeedbackMessageDto getMessage(UUID currentUserId, UUID messageId);

    // ------------------------------------------------------------------
    // Authoring
    // ------------------------------------------------------------------

    /** Publishes a message to the feed. It starts at status {@code sent} with no votes. */
    FeedbackMessageDto submit(UUID currentUserId, SubmitFeedbackMessageRequest request);

    /**
     * Deletes the caller's own message outright — the row goes, and its votes cascade with it.
     * Only allowed while the message is still {@code sent}: once staff have moved it onto the
     * roadmap it belongs to the community, and only they can take it down. Staff removal is a
     * different operation ({@link #removeAsStaff}) and keeps the row.
     *
     * @throws com.carsocialmedia.backend.feedbackfeed.exception.NotFeedbackAuthorException
     *         if the caller did not write it
     * @throws com.carsocialmedia.backend.feedbackfeed.exception.FeedbackDeletionClosedException
     *         if staff have already moved it to {@code under_development} or {@code completed}
     */
    void deleteOwn(UUID currentUserId, UUID messageId);

    // ------------------------------------------------------------------
    // Voting
    // ------------------------------------------------------------------

    /**
     * Casts a vote and returns the message with fresh counts. Sending the direction the caller
     * already holds withdraws the vote; the opposite direction switches it.
     *
     * @param value {@code 1} or {@code -1}
     * @throws com.carsocialmedia.backend.feedbackfeed.exception.FeedbackVotingClosedException
     *         if the message has already shipped
     */
    FeedbackMessageDto vote(UUID currentUserId, UUID messageId, int value);

    /** Withdraws the caller's vote, whichever way it went. Idempotent. */
    FeedbackMessageDto removeVote(UUID currentUserId, UUID messageId);

    // ------------------------------------------------------------------
    // Admin — called by the admin module, which applies the capability check
    // ------------------------------------------------------------------

    /**
     * One page of the dashboard's list: every message, newest first, optionally narrowed by
     * category and status, and including staff-removed rows.
     */
    FeedbackFeedPageDto listAll(String type, String status, boolean includeRemoved, String cursor, int size);

    /**
     * The most-voted messages that have not shipped yet, highest net votes first — the dashboard's
     * "top 3" panel. Nothing is persisted; this is a plain read.
     */
    List<FeedbackMessageDto> listTopVoted(int limit);

    /**
     * Moves a message along the roadmap. The author's in-app notification is written by a Supabase
     * trigger on the status column, so there is deliberately no fan-out here.
     */
    FeedbackMessageDto updateStatus(UUID messageId, String statusId);

    /**
     * Writes, replaces or (with a blank response) clears the team's official reply. Silent by
     * design — only a status change notifies the author.
     */
    FeedbackMessageDto respond(UUID messageId, String response);

    /**
     * Staff removal of spam or abuse: flags the row {@code is_deleted} so it disappears from every
     * user-facing read while surviving for audit. Idempotent.
     */
    void removeAsStaff(UUID messageId);
}

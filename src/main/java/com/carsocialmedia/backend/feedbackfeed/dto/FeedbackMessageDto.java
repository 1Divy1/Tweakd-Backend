package com.carsocialmedia.backend.feedbackfeed.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One feedback card, as shown in the community feed, in the completed-requests section and on the
 * dashboard.
 *
 * <p>{@code message} and {@code staffResponse} are always returned side by side. The staff response
 * is rendered <em>beneath</em> the author's original message, never in place of it — the backend
 * does no substitution.
 *
 * @param message       the author's original text, always present
 * @param staffResponse the team's official reply, or {@code null} if none has been written
 * @param myVote        the calling user's vote — {@code 1}, {@code -1}, or {@code null} if they
 *                      have not voted
 * @param viewerIsAuthor whether the caller wrote this message (the card's "you" marker)
 * @param completedAt   when the message shipped, or {@code null} unless the status is
 *                      {@code completed}; backs the "shipped 3d ago" label
 * @param deleted       staff removal flag. Always {@code false} in user-facing reads, which filter
 *                      these out; only the dashboard ever sees {@code true}
 */
public record FeedbackMessageDto(
        UUID id,
        FeedbackAuthorDto author,
        String message,
        FeedbackCategoryDto type,
        FeedbackFeedStatusDto status,
        String staffResponse,
        int upVotes,
        int downVotes,
        int netVotes,
        Integer myVote,
        boolean viewerIsAuthor,
        boolean deleted,
        Instant createdAt,
        Instant completedAt
) {}

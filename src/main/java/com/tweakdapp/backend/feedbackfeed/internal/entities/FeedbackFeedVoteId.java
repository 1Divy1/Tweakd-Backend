package com.tweakdapp.backend.feedbackfeed.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key of {@link FeedbackFeedVoteEntity} — one vote per user per message. */
@Embeddable
public class FeedbackFeedVoteId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    protected FeedbackFeedVoteId() {
    }

    public FeedbackFeedVoteId(UUID userId, UUID messageId) {
        this.userId = userId;
        this.messageId = messageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof FeedbackFeedVoteId other
                && Objects.equals(userId, other.userId)
                && Objects.equals(messageId, other.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, messageId);
    }
}

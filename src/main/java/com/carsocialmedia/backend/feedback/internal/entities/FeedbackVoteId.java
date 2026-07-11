package com.carsocialmedia.backend.feedback.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class FeedbackVoteId implements Serializable {

    @Column(name = "feedback_id")
    private UUID feedbackId;

    @Column(name = "user_id")
    private UUID userId;

    public FeedbackVoteId(UUID feedbackId, UUID userId) {
        this.feedbackId = feedbackId;
        this.userId = userId;
    }
}

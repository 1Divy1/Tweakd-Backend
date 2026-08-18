package com.carsocialmedia.backend.feedbackfeed.internal.repositories;

import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedVoteEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedVoteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedbackFeedVoteRepository extends JpaRepository<FeedbackFeedVoteEntity, FeedbackFeedVoteId> {

    /**
     * The caller's votes across one page of the feed, fetched in a single round trip so each card
     * can show which way they voted without an N+1.
     */
    List<FeedbackFeedVoteEntity> findByIdUserIdAndIdMessageIdIn(UUID userId, Collection<UUID> messageIds);
}

package com.tweakdapp.backend.feedback.internal.repositories;

import com.tweakdapp.backend.feedback.internal.entities.FeedbackSubscriptionEntity;
import com.tweakdapp.backend.feedback.internal.entities.FeedbackSubscriptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedbackSubscriptionRepository
        extends JpaRepository<FeedbackSubscriptionEntity, FeedbackSubscriptionId> {

    /** Race-safe idempotent subscribe — see the vote repository for why ON CONFLICT. */
    @Modifying
    @Query(value = """
            insert into feedback_subscriptions (feedback_id, user_id)
            values (:feedbackId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("feedbackId") UUID feedbackId, @Param("userId") UUID userId);

    void deleteByIdFeedbackIdAndIdUserId(UUID feedbackId, UUID userId);

    /** Everyone to notify when this feedback's status changes. */
    @Query("select s.id.userId from FeedbackSubscriptionEntity s where s.id.feedbackId = :feedbackId")
    List<UUID> findSubscriberIds(@Param("feedbackId") UUID feedbackId);

    /** Which of the given feedback ids the viewer subscribed to — one query per board page. */
    @Query("""
            select s.id.feedbackId from FeedbackSubscriptionEntity s
            where s.id.userId = :userId and s.id.feedbackId in :feedbackIds
            """)
    List<UUID> findSubscribedFeedbackIds(@Param("userId") UUID userId,
                                         @Param("feedbackIds") Collection<UUID> feedbackIds);
}

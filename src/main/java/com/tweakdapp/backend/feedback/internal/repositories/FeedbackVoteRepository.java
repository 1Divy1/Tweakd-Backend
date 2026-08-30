package com.tweakdapp.backend.feedback.internal.repositories;

import com.tweakdapp.backend.feedback.internal.entities.FeedbackVoteEntity;
import com.tweakdapp.backend.feedback.internal.entities.FeedbackVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedbackVoteRepository extends JpaRepository<FeedbackVoteEntity, FeedbackVoteId> {

    /**
     * {@code ON CONFLICT DO NOTHING} makes concurrent double-taps race-safe — a plain
     * exists-then-insert can have both requests pass the check and the loser blow up on the
     * composite-PK constraint. The vote_count trigger only fires when a row is actually inserted.
     */
    @Modifying
    @Query(value = """
            insert into feedback_votes (feedback_id, user_id)
            values (:feedbackId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("feedbackId") UUID feedbackId, @Param("userId") UUID userId);

    void deleteByIdFeedbackIdAndIdUserId(UUID feedbackId, UUID userId);

    /** Which of the given feedback ids the viewer has upvoted — one query per board page. */
    @Query("""
            select v.id.feedbackId from FeedbackVoteEntity v
            where v.id.userId = :userId and v.id.feedbackId in :feedbackIds
            """)
    List<UUID> findVotedFeedbackIds(@Param("userId") UUID userId,
                                    @Param("feedbackIds") Collection<UUID> feedbackIds);
}

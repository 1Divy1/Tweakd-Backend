package com.carsocialmedia.backend.feedback.internal.repositories;

import com.carsocialmedia.backend.feedback.internal.entities.FeedbackCommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedbackCommentRepository extends JpaRepository<FeedbackCommentEntity, UUID> {

    List<FeedbackCommentEntity> findByFeedbackIdOrderByCreatedAtDescIdDesc(UUID feedbackId, Pageable pageable);

    /** Keyset continuation of {@link #findByFeedbackIdOrderByCreatedAtDescIdDesc}. */
    @Query("""
            select c from FeedbackCommentEntity c
            where c.feedbackId = :feedbackId
              and (c.createdAt < :createdAt or (c.createdAt = :createdAt and c.id < :id))
            order by c.createdAt desc, c.id desc
            """)
    List<FeedbackCommentEntity> findPageAfter(@Param("feedbackId") UUID feedbackId,
                                              @Param("createdAt") Instant createdAt,
                                              @Param("id") UUID id,
                                              Pageable pageable);
}

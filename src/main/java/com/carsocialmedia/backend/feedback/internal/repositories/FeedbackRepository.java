package com.carsocialmedia.backend.feedback.internal.repositories;

import com.carsocialmedia.backend.feedback.internal.entities.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, UUID> {

    /** Every feedback the given user submitted, newest first — for their "my feedback" view. */
    List<FeedbackEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

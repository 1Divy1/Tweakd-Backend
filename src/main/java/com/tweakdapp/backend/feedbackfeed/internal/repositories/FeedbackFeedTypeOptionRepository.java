package com.tweakdapp.backend.feedbackfeed.internal.repositories;

import com.tweakdapp.backend.feedbackfeed.internal.entities.FeedbackFeedTypeOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackFeedTypeOptionRepository extends JpaRepository<FeedbackFeedTypeOptionEntity, String> {
}

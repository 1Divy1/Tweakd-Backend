package com.carsocialmedia.backend.feedbackfeed.internal.repositories;

import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedStatusOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackFeedStatusOptionRepository extends JpaRepository<FeedbackFeedStatusOptionEntity, String> {
}

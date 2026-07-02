package com.carsocialmedia.backend.feedback.internal.repositories;

import com.carsocialmedia.backend.feedback.internal.entities.FeedbackStatusOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackStatusOptionRepository extends JpaRepository<FeedbackStatusOptionEntity, String> {
}

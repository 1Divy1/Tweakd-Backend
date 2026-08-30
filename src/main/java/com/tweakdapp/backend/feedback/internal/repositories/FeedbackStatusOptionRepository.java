package com.tweakdapp.backend.feedback.internal.repositories;

import com.tweakdapp.backend.feedback.internal.entities.FeedbackStatusOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackStatusOptionRepository extends JpaRepository<FeedbackStatusOptionEntity, String> {
}
